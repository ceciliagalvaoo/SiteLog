package hr.algebra.sitelog.audit.aspect;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import hr.algebra.sitelog.audit.domain.DomainActionType;

/**
 * Marks a service method as a domain action worth recording: {@link DomainActionAspect} writes
 * who called it, when, on which record and how it ended into {@code domain_action} and the
 * {@code sitelog.audit} log. Put it on public methods of Spring beans only (the aspect works
 * through the bean's proxy, so a call from inside the same class is not intercepted).
 *
 * <pre>
 * &#64;AuditedAction(DomainActionType.INCIDENT_SIGNED_OFF)
 * public void signOff(Long id, Long expectedVersion) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditedAction {

	/** The action; its {@link DomainActionType#entityType()} says what kind of record the id is. */
	DomainActionType value();

	/** Where the id of the record comes from. */
	IdSource id() default IdSource.FIRST_ARGUMENT;

	enum IdSource {
		/** The first argument, a {@code Long} (e.g. {@code signOff(Long id, ...)}). */
		FIRST_ARGUMENT,
		/** The returned {@code Long}: a create method whose record has no id before the call. */
		RETURN_VALUE
	}

}
