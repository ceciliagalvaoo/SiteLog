package hr.algebra.sitelog.api.error;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import hr.algebra.sitelog.api.common.InvalidPageRequestException;
import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.DomainException;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.common.exception.LogLockedException;
import hr.algebra.sitelog.common.exception.MissingSeverityFieldsException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.common.web.WebExceptionHandler;
import hr.algebra.sitelog.incident.domain.CorrectiveAction;
import hr.algebra.sitelog.incident.domain.Incident;
import jakarta.validation.ConstraintViolation;

/**
 * Every error of the REST API as an RFC 9457 {@link ProblemDetail}
 * ({@code application/problem+json}): {@code type}, {@code title}, {@code status},
 * {@code detail}, {@code instance} (the request path), plus {@code code} (the stable message
 * key) and, for field errors, {@code errors} (a list of {@link FieldViolation}).
 *
 * <table>
 * <caption>Status codes</caption>
 * <tr><td>400</td><td>Bean Validation of the request body, malformed JSON, wrong parameter type,
 * missing {@code version} parameter, unknown sort property</td></tr>
 * <tr><td>403</td><td>{@code @PreAuthorize} of the service refused the call (wrong role, not the
 * manager of the site)</td></tr>
 * <tr><td>404</td><td>record not found</td></tr>
 * <tr><td>409</td><td>conflict with the current state: stale {@code version} or concurrent commit
 * ({@code @Version}), signed-off (locked) log, workflow step not allowed, delete refused,
 * database constraint or lock trigger</td></tr>
 * <tr><td>422</td><td>well-formed request refused by a business rule on one field (duplicate
 * code, future date, inactive crew...) or, for a HIGH / CRITICAL incident, by the AR2 rule on
 * its extra fields (every missing field in {@code errors})</td></tr>
 * <tr><td>500</td><td>anything else, without internals in the body</td></tr>
 * </table>
 *
 * <p>Scope: only the controllers of {@code hr.algebra.sitelog.api}. {@code @Order(HIGHEST_PRECEDENCE)}
 * because {@link WebExceptionHandler} (screens: flash messages and error pages) is selected by
 * {@code @Controller}, which {@code @RestController} also carries; Spring uses the first advice,
 * in order, that has a handler for the exception, so this one must come first. The screens
 * never reach this class (not in the package), so their behaviour is unchanged.
 *
 * <p>401 (not signed in) and 403 for a missing CSRF token happen in the security filter chain,
 * before any controller; {@code ApiAuthenticationEntryPoint} and {@code ApiAccessDeniedHandler}
 * write the same ProblemDetail shape there.
 */
@RestControllerAdvice(basePackages = "hr.algebra.sitelog.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	static final String CODE = "code";

	static final String ERRORS = "errors";

	static final String VALIDATION_DETAIL = "The request has invalid fields; see 'errors'.";

	static final String CONFLICT_KEY = "dailyLog.flash.conflict";

	static final String INCIDENT_CONFLICT_KEY = "incident.flash.conflict";

	/** Prefix of the field message keys of AR2, e.g. {@code validation.incident.rootCause.required}. */
	static final String SEVERITY_FIELD_KEY = "validation.incident.%s.required";

	static final String LOCKED_KEY = "dailyLog.flash.locked";

	static final String DATA_CONFLICT_KEY = "error.conflict.data";

	static final String FORBIDDEN_KEY = "error.403.body";

	static final String SERVER_ERROR_KEY = "error.500.body";

	/** SQLSTATE and message of the V3 lock trigger (same check as {@link WebExceptionHandler}). */
	static final String LOCK_TRIGGER_SQLSTATE = "P0001";

	static final String LOCK_TRIGGER_MESSAGE = "daily_log_locked";

	private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

	private final MessageSource messages;

	public ApiExceptionHandler(MessageSource messages) {
		this.messages = messages;
	}

	// ---------------------------------------------------------------- 400: request format

	/** {@code @Valid @RequestBody} failed: one {@link FieldViolation} per field (or class-level) error. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldViolation> errors = ex.getBindingResult().getAllErrors().stream().map(this::violation).toList();
		return problem(HttpStatus.BAD_REQUEST, VALIDATION_DETAIL, "validation.request.invalid", errors);
	}

	/** Constraint on a handler parameter (method validation) failed. */
	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldViolation> errors = ex.getParameterValidationResults().stream()
			.flatMap(result -> result.getResolvableErrors().stream()
				.map(error -> new FieldViolation(result.getMethodParameter().getParameterName(),
						error.getCodes() == null || error.getCodes().length == 0 ? null : error.getCodes()[0],
						messages.getMessage(error, locale()))))
			.toList();
		return problem(HttpStatus.BAD_REQUEST, VALIDATION_DETAIL, "validation.request.invalid", errors);
	}

	@ExceptionHandler(InvalidPageRequestException.class)
	public ResponseEntity<Object> invalidPageRequest(InvalidPageRequestException ex) {
		return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), "validation.page.invalid", null);
	}

	// ---------------------------------------------------------------- domain exceptions

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<Object> notFound(NotFoundException ex) {
		return domainProblem(HttpStatus.NOT_FOUND, ex);
	}

	/** A business rule on one field: 422 with the field in {@code errors} (same key as the form shows). */
	@ExceptionHandler(InvalidFieldException.class)
	public ResponseEntity<Object> invalidField(InvalidFieldException ex) {
		String text = text(ex.getMessageKey(), ex.getArgs());
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, text, ex.getMessageKey(),
				List.of(new FieldViolation(ex.getField(), ex.getMessageKey(), text)));
	}

	/**
	 * AR2: a HIGH / CRITICAL incident lacks fields its severity requires. One {@link FieldViolation}
	 * per missing field (same keys as the form), so a client can mark all of them at once.
	 */
	@ExceptionHandler(MissingSeverityFieldsException.class)
	public ResponseEntity<Object> missingSeverityFields(MissingSeverityFieldsException ex) {
		List<FieldViolation> errors = ex.getFields().stream().map(field -> {
			String key = SEVERITY_FIELD_KEY.formatted(field);
			return new FieldViolation(field, key, text(key));
		}).toList();
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, text(ex.getMessageKey(), ex.getArgs()), ex.getMessageKey(),
				errors);
	}

	/** AR1: the log is signed off; it must be reopened before any change. */
	@ExceptionHandler({ LogLockedException.class, InvalidTransitionException.class,
			DeletionNotAllowedException.class })
	public ResponseEntity<Object> conflict(DomainException ex) {
		return domainProblem(HttpStatus.CONFLICT, ex);
	}

	/**
	 * Any other business exception (for example the severity rules of incidents added later):
	 * the request was understood but a rule refused it.
	 */
	@ExceptionHandler(DomainException.class)
	public ResponseEntity<Object> businessRule(DomainException ex) {
		return domainProblem(HttpStatus.UNPROCESSABLE_CONTENT, ex);
	}

	// ---------------------------------------------------------------- concurrency and database

	/**
	 * The {@code version} sent by the client is not the current one, or a concurrent transaction
	 * committed first ({@code @Version}): the client must GET the record again and retry. The
	 * text names the aggregate that changed: an incident (its corrective actions bump the
	 * incident's version) or, for everything else, the daily log.
	 */
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ResponseEntity<Object> optimisticLock(ObjectOptimisticLockingFailureException ex) {
		LOG.info("Optimistic lock conflict on {} {}", ex.getPersistentClassName(), ex.getIdentifier());
		String key = isIncidentAggregate(ex.getPersistentClassName()) ? INCIDENT_CONFLICT_KEY : CONFLICT_KEY;
		return problem(HttpStatus.CONFLICT, text(key), key, null);
	}

	static boolean isIncidentAggregate(String persistentClassName) {
		return Incident.class.getName().equals(persistentClassName)
				|| CorrectiveAction.class.getName().equals(persistentClassName);
	}

	/** Second line of defence: a rule caught only by a constraint or the V3 lock trigger. */
	@ExceptionHandler(DataAccessException.class)
	public ResponseEntity<Object> dataAccess(DataAccessException ex) {
		if (isLockTrigger(ex)) {
			LOG.warn("Lock trigger rejected a write the service did not stop: {}", ex.getMostSpecificCause().getMessage());
			return problem(HttpStatus.CONFLICT, text(LOCKED_KEY), LOCKED_KEY, null);
		}
		if (ex instanceof DataIntegrityViolationException) {
			LOG.warn("Constraint violation: {}", ex.getMostSpecificCause().getMessage());
			return problem(HttpStatus.CONFLICT, text(DATA_CONFLICT_KEY), DATA_CONFLICT_KEY, null);
		}
		return serverError(ex);
	}

	// ---------------------------------------------------------------- security and fallback

	/** {@code @PreAuthorize} on the service refused the call (the caller is signed in). */
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<Object> accessDenied(AccessDeniedException ex) {
		return problem(HttpStatus.FORBIDDEN, text(FORBIDDEN_KEY), FORBIDDEN_KEY, null);
	}

	/** Unexpected error: logged with the stack trace, the client gets no internals. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> unexpected(Exception ex) {
		return serverError(ex);
	}

	private ResponseEntity<Object> serverError(Exception ex) {
		LOG.error("Unexpected error in the REST API", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, text(SERVER_ERROR_KEY), SERVER_ERROR_KEY, null);
	}

	// ---------------------------------------------------------------- helpers

	/**
	 * The V3 trigger on a signed-off log: SQLSTATE {@code P0001} (any {@code RAISE EXCEPTION})
	 * <b>and</b> the {@code daily_log_locked} message, so the append-only audit trigger (same
	 * SQLSTATE) is not mistaken for it.
	 */
	static boolean isLockTrigger(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
			if (cause instanceof SQLException sql && LOCK_TRIGGER_SQLSTATE.equals(sql.getSQLState())
					&& sql.getMessage() != null && sql.getMessage().contains(LOCK_TRIGGER_MESSAGE)) {
				return true;
			}
		}
		return false;
	}

	private ResponseEntity<Object> domainProblem(HttpStatus status, DomainException ex) {
		return problem(status, text(ex.getMessageKey(), ex.getArgs()), ex.getMessageKey(), null);
	}

	private static ResponseEntity<Object> problem(HttpStatus status, String detail, String code,
			List<FieldViolation> errors) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
		body.setProperty(CODE, code);
		if (errors != null) {
			body.setProperty(ERRORS, errors);
		}
		return ResponseEntity.status(status).body(body);
	}

	/**
	 * Field and class-level Bean Validation errors. {@code code} is the key of the message
	 * template ({@code {validation.site.code.required}} gives {@code validation.site.code.required}),
	 * the same key the screens use; {@code message} was interpolated in the request's language.
	 */
	private FieldViolation violation(ObjectError error) {
		String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
		String code = error.getCode();
		if (error.contains(ConstraintViolation.class)) {
			String template = error.unwrap(ConstraintViolation.class).getMessageTemplate();
			if (template.startsWith("{") && template.endsWith("}")) {
				code = template.substring(1, template.length() - 1);
			}
		}
		return new FieldViolation(field, code, messages.getMessage(error, locale()));
	}

	private String text(String key, Object... args) {
		return messages.getMessage(key, args, key, locale());
	}

	private static Locale locale() {
		return LocaleContextHolder.getLocale();
	}

}
