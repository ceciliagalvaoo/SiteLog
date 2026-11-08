package hr.algebra.sitelog.common.exception;

/**
 * A delete refused by a business rule (HTTP 409): the record is referenced by data that must be
 * kept (daily logs and incidents are records, not disposable children). The message key tells
 * the user what to do instead, e.g. set an end date or deactivate the crew.
 */
public class DeletionNotAllowedException extends DomainException {

	public DeletionNotAllowedException(String messageKey, String detail) {
		super(messageKey, detail);
	}

}
