package hr.algebra.sitelog.audit.aspect;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import hr.algebra.sitelog.audit.aspect.AuditedAction.IdSource;
import hr.algebra.sitelog.audit.domain.ActionOutcome;
import hr.algebra.sitelog.audit.service.DomainActionRecorder;
import hr.algebra.sitelog.common.exception.DomainException;
import hr.algebra.sitelog.user.security.AppUserPrincipal;

/**
 * The domain aspect (ADR-0003 decision 6, "which action happened and by whom").
 *
 * <p><b>What it intercepts:</b> every Spring bean method annotated with {@link AuditedAction}
 * (pointcut {@code @annotation(AuditedAction)}): daily log sign-off, reopen request / approval /
 * rejection, the incident workflow (report, update, start review, sign-off, close, delete) and
 * corrective action changes. For each call it records the action, the record id, the signed-in
 * user, the time, the duration and the outcome (SUCCESS, REJECTED by a business rule, DENIED
 * by {@code @PreAuthorize}, FAILED) - as one structured line in the {@code sitelog.audit} log
 * and one row in {@code domain_action}. The exception itself is always re-thrown unchanged:
 * the aspect observes, it never changes the result of the call.
 *
 * <p><b>Order:</b> {@link Ordered#HIGHEST_PRECEDENCE}, so it is the outermost advice of the
 * service proxy - outside method security and outside the service transaction. That is why it
 * sees refused calls (DENIED) and failures that only happen at commit (an optimistic lock
 * conflict, a trigger), and why a SUCCESS row is written only after the business transaction
 * committed. The row is written by {@link DomainActionRecorder} in its own transaction (or in
 * the caller's, when an outer transaction exists, so it rolls back together with it).
 *
 * <p>Recording must never break the business call: a failure to write the row is logged and
 * swallowed; the log line is written first, so it is never lost.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DomainActionAspect {

	/** Dedicated logger: can be routed to its own appender / log index in production. */
	static final Logger AUDIT_LOG = LoggerFactory.getLogger("sitelog.audit");

	private static final Logger LOG = LoggerFactory.getLogger(DomainActionAspect.class);

	private final DomainActionRecorder recorder;

	private final Clock clock;

	public DomainActionAspect(DomainActionRecorder recorder, Clock clock) {
		this.recorder = recorder;
		this.clock = clock;
	}

	/**
	 * The annotation is read from the method instead of being bound as an advice parameter
	 * ({@code @annotation(audited)}): argument binding needs Spring's ExposeInvocationInterceptor
	 * to run first, and this advice is deliberately the very first one in the chain.
	 */
	@Around("@annotation(hr.algebra.sitelog.audit.aspect.AuditedAction)")
	public Object record(ProceedingJoinPoint joinPoint) throws Throwable {
		AuditedAction audited = annotationOf(joinPoint);
		Instant startedAt = Instant.now(clock);
		long start = System.nanoTime();
		Actor actor = Actor.current();
		Object result;
		try {
			result = joinPoint.proceed();
		}
		catch (Throwable ex) {
			write(audited, entityId(joinPoint, audited, null), actor, startedAt, start, outcomeOf(ex), detailOf(ex));
			throw ex;
		}
		write(audited, entityId(joinPoint, audited, result), actor, startedAt, start, ActionOutcome.SUCCESS, null);
		return result;
	}

	private void write(AuditedAction audited, Long entityId, Actor actor, Instant startedAt, long start,
			ActionOutcome outcome, String detail) {
		int durationMs = (int) Math.min(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), Integer.MAX_VALUE);
		AUDIT_LOG.info("domain_action action={} entity={} id={} actor={} outcome={} durationMs={}{}", audited.value(),
				audited.value().entityType(), entityId, actor.email(), outcome, durationMs,
				detail == null ? "" : " detail=" + detail);
		try {
			recorder.record(audited.value(), entityId, actor.id(), actor.email(), startedAt, outcome, durationMs,
					detail);
		}
		catch (RuntimeException e) {
			LOG.error("Could not store domain action {} on {} {}", audited.value(), audited.value().entityType(),
					entityId, e);
		}
	}

	static AuditedAction annotationOf(ProceedingJoinPoint joinPoint) {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		Class<?> targetClass = joinPoint.getTarget() == null ? signature.getDeclaringType()
				: AopUtils.getTargetClass(joinPoint.getTarget());
		AuditedAction audited = AnnotatedElementUtils.findMergedAnnotation(
				AopUtils.getMostSpecificMethod(signature.getMethod(), targetClass), AuditedAction.class);
		return audited != null ? audited : signature.getMethod().getAnnotation(AuditedAction.class);
	}

	static Long entityId(ProceedingJoinPoint joinPoint, AuditedAction audited, Object result) {
		if (audited.id() == IdSource.RETURN_VALUE) {
			return result instanceof Long id ? id : null;
		}
		Object[] args = joinPoint.getArgs();
		return args.length > 0 && args[0] instanceof Long id ? id : null;
	}

	static ActionOutcome outcomeOf(Throwable ex) {
		if (ex instanceof AccessDeniedException) {
			return ActionOutcome.DENIED;
		}
		if (ex instanceof DomainException) {
			return ActionOutcome.REJECTED;
		}
		return ActionOutcome.FAILED;
	}

	/** The message key of a business exception, otherwise only the exception class (never a message with data). */
	static String detailOf(Throwable ex) {
		return ex instanceof DomainException domain ? domain.getMessageKey() : ex.getClass().getSimpleName();
	}

	/** Who is calling, read from the security context without a database query. */
	record Actor(Long id, String email) {

		static final Actor SYSTEM = new Actor(null, null);

		static Actor current() {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication == null || !authentication.isAuthenticated()
					|| authentication instanceof AnonymousAuthenticationToken) {
				return SYSTEM;
			}
			Long id = authentication.getPrincipal() instanceof AppUserPrincipal principal ? principal.getId() : null;
			return new Actor(id, authentication.getName());
		}

	}

}
