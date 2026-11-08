package hr.algebra.sitelog.common.exception;

/** A workflow step that is not allowed from the record's current status (HTTP 409). */
public class InvalidTransitionException extends DomainException {

	public InvalidTransitionException(String messageKey, String detail) {
		super(messageKey, detail);
	}

}
