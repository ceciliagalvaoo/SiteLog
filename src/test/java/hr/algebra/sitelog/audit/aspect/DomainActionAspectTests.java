package hr.algebra.sitelog.audit.aspect;

import static hr.algebra.sitelog.support.TestEntities.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import hr.algebra.sitelog.audit.aspect.AuditedAction.IdSource;
import hr.algebra.sitelog.audit.domain.ActionOutcome;
import hr.algebra.sitelog.audit.domain.DomainActionType;
import hr.algebra.sitelog.audit.service.DomainActionRecorder;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.support.TestEntities;
import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.security.AppUserPrincipal;

/**
 * The aspect on a plain target behind a real AspectJ proxy: what it intercepts (only annotated
 * methods), what it records (action, record id, user, time, outcome) and that it never changes
 * the result or the exception of the call.
 */
class DomainActionAspectTests {

	/** Stand-in for a service: one annotated method per case, one method that is not annotated. */
	static class Target {

		@AuditedAction(DomainActionType.INCIDENT_SIGNED_OFF)
		public String signOff(Long id, Long version) {
			return "signed " + id;
		}

		@AuditedAction(value = DomainActionType.INCIDENT_REPORTED, id = IdSource.RETURN_VALUE)
		public Long report(String title) {
			return 42L;
		}

		@AuditedAction(DomainActionType.INCIDENT_CLOSED)
		public void close(Long id) {
			throw new InvalidTransitionException("incident.flash.close.openActions", "open actions");
		}

		@AuditedAction(DomainActionType.LOG_REOPEN_APPROVED)
		public void approve(Long id) {
			throw new AccessDeniedException("no");
		}

		@AuditedAction(DomainActionType.LOG_SIGNED_OFF)
		public void conflict(Long id) {
			throw new ObjectOptimisticLockingFailureException(Object.class, id);
		}

		public String notAudited(Long id) {
			return "plain";
		}

	}

	private final DomainActionRecorder recorder = mock(DomainActionRecorder.class);

	private Target proxy;

	@BeforeEach
	void setUp() {
		AspectJProxyFactory factory = new AspectJProxyFactory(new Target());
		factory.setProxyTargetClass(true);
		factory.addAspect(new DomainActionAspect(recorder, TestEntities.fixedClock()));
		proxy = factory.getProxy();
		AppUserPrincipal principal = new AppUserPrincipal(9L, "supervisor@sitelog.test", "x", "Ivana Babic",
				RoleName.SAFETY_SUPERVISOR, "en", true);
		SecurityContextHolder.getContext()
			.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
	}

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void successfulCall_isRecordedWithTheFirstArgumentAsRecordIdAndTheSignedInUser() {
		assertThat(proxy.signOff(30L, 4L)).isEqualTo("signed 30");

		verify(recorder).record(eq(DomainActionType.INCIDENT_SIGNED_OFF), eq(30L), eq(9L),
				eq("supervisor@sitelog.test"), eq(NOW), eq(ActionOutcome.SUCCESS), anyInt(), isNull());
	}

	@Test
	void createMethod_takesTheRecordIdFromTheReturnValue() {
		assertThat(proxy.report("Fall")).isEqualTo(42L);

		verify(recorder).record(eq(DomainActionType.INCIDENT_REPORTED), eq(42L), eq(9L), any(), any(),
				eq(ActionOutcome.SUCCESS), anyInt(), isNull());
	}

	@Test
	void businessRuleRefusal_isRecordedAsRejectedWithTheMessageKey_andRethrownUnchanged() {
		assertThatThrownBy(() -> proxy.close(30L)).isInstanceOf(InvalidTransitionException.class);

		verify(recorder).record(eq(DomainActionType.INCIDENT_CLOSED), eq(30L), eq(9L), any(), any(),
				eq(ActionOutcome.REJECTED), anyInt(), eq("incident.flash.close.openActions"));
	}

	@Test
	void accessDenied_isRecordedAsDenied() {
		assertThatThrownBy(() -> proxy.approve(10L)).isInstanceOf(AccessDeniedException.class);

		verify(recorder).record(eq(DomainActionType.LOG_REOPEN_APPROVED), eq(10L), eq(9L), any(), any(),
				eq(ActionOutcome.DENIED), anyInt(), eq("AccessDeniedException"));
	}

	@Test
	void anyOtherFailure_isRecordedAsFailedWithTheExceptionClassOnly() {
		assertThatThrownBy(() -> proxy.conflict(10L)).isInstanceOf(ObjectOptimisticLockingFailureException.class);

		verify(recorder).record(eq(DomainActionType.LOG_SIGNED_OFF), eq(10L), any(), any(), any(),
				eq(ActionOutcome.FAILED), anyInt(), eq("ObjectOptimisticLockingFailureException"));
	}

	@Test
	void methodWithoutTheAnnotation_isNotIntercepted() {
		assertThat(proxy.notAudited(1L)).isEqualTo("plain");

		verifyNoInteractions(recorder);
	}

	@Test
	void callWithoutASignedInUser_isRecordedWithoutActor() {
		SecurityContextHolder.clearContext();

		proxy.signOff(30L, 4L);

		verify(recorder).record(eq(DomainActionType.INCIDENT_SIGNED_OFF), eq(30L), isNull(), isNull(), any(),
				eq(ActionOutcome.SUCCESS), anyInt(), isNull());
	}

	@Test
	void userWithoutApplicationPrincipal_isRecordedByNameOnly() {
		SecurityContextHolder.getContext().setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated("mock@sitelog.test", null, java.util.List.of()));

		proxy.signOff(30L, 4L);

		verify(recorder).record(any(), eq(30L), isNull(), eq("mock@sitelog.test"), any(), any(), anyInt(), any());
	}

	@Test
	void failureToStoreTheRecord_doesNotBreakTheBusinessCall() {
		doThrow(new IllegalStateException("db down")).when(recorder)
			.record(any(), any(), any(), any(), any(), any(), anyInt(), any());

		assertThat(proxy.signOff(30L, 4L)).isEqualTo("signed 30");
	}

}
