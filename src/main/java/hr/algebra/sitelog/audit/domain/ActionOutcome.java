package hr.algebra.sitelog.audit.domain;

/** How an intercepted call ended. Values match {@code ck_domain_action_outcome} in V7. */
public enum ActionOutcome {
	/** The method returned normally (and, outside a caller's transaction, its changes committed). */
	SUCCESS,
	/** A business rule refused it ({@code DomainException}: locked log, invalid transition, missing fields...). */
	REJECTED,
	/** {@code @PreAuthorize} refused it ({@code AccessDeniedException}, 403). */
	DENIED,
	/** Any other exception (optimistic lock conflict at commit, database error...). */
	FAILED
}
