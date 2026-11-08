package hr.algebra.sitelog.common.exception;

/**
 * A business validation that belongs to one input field and needs the database to decide
 * (uniqueness, a referenced record that must be active...). HTTP 422 in the API; on screens the
 * controller adds it to the {@code BindingResult} as a field error and re-renders the form.
 *
 * <p>{@link #getField()} is the property path of the command, which mirrors the form object
 * (for example {@code workEntries[0].crewAssignments[1].crewId}).
 */
public class InvalidFieldException extends DomainException {

	private final String field;

	public InvalidFieldException(String field, String messageKey) {
		super(messageKey, "invalid value for " + field + " (" + messageKey + ")");
		this.field = field;
	}

	public String getField() {
		return field;
	}

}
