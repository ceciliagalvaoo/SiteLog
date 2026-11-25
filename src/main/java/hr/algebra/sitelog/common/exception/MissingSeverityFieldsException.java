package hr.algebra.sitelog.common.exception;

import java.util.List;

/**
 * AR2: a HIGH or CRITICAL incident lacks fields its severity requires (root cause, witnesses,
 * medical treatment, authority notified). HTTP 422 in the API; on screens each field becomes a
 * field error ({@code validation.incident.<field>.required}) or, for a workflow button, an error
 * flash. {@code ck_incident_severity_fields} (V3) is the database safety net for the same rule.
 */
public class MissingSeverityFieldsException extends DomainException {

	public static final String MESSAGE_KEY = "incident.flash.missingFields";

	private final transient List<String> fields;

	public MissingSeverityFieldsException(Long incidentId, List<String> fields) {
		super(MESSAGE_KEY, "incident " + incidentId + " is missing " + fields);
		this.fields = List.copyOf(fields);
	}

	/** Property names of the missing fields, in form order. */
	public List<String> getFields() {
		return fields;
	}

}
