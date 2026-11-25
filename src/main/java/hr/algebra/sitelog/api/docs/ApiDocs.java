package hr.algebra.sitelog.api.docs;

/**
 * Example bodies shown in the OpenAPI document (Swagger UI) for the error responses. Constants,
 * because annotation values must be compile-time constants. They are the real shapes produced by
 * {@code ApiExceptionHandler}.
 */
public final class ApiDocs {

	public static final String PROBLEM_JSON = "application/problem+json";

	public static final String BAD_REQUEST = """
			{"type":"about:blank","title":"Bad Request","status":400,
			 "detail":"The request has invalid fields; see 'errors'.","instance":"/api/v1/sites",
			 "code":"validation.request.invalid",
			 "errors":[{"field":"code","code":"validation.site.code.required","message":"Code is required"}]}""";

	public static final String NOT_FOUND = """
			{"type":"about:blank","title":"Not Found","status":404,
			 "detail":"The page or record you are looking for does not exist.","instance":"/api/v1/sites/999",
			 "code":"error.notFound"}""";

	public static final String FORBIDDEN = """
			{"type":"about:blank","title":"Forbidden","status":403,
			 "detail":"You do not have permission to open this page or perform this action.",
			 "instance":"/api/v1/sites","code":"error.403.body"}""";

	public static final String CONFLICT_VERSION = """
			{"type":"about:blank","title":"Conflict","status":409,
			 "detail":"This daily log was changed by someone else. Reload and try again.",
			 "instance":"/api/v1/daily-logs/42","code":"dailyLog.flash.conflict"}""";

	public static final String CONFLICT_LOCKED = """
			{"type":"about:blank","title":"Conflict","status":409,
			 "detail":"This daily log is signed off and cannot be changed. Request a reopen to correct it.",
			 "instance":"/api/v1/daily-logs/42","code":"dailyLog.flash.locked"}""";

	public static final String CONFLICT_SITE_DELETE = """
			{"type":"about:blank","title":"Conflict","status":409,
			 "detail":"This site has daily logs or incidents and cannot be deleted. Set an end date to finish it.",
			 "instance":"/api/v1/sites/3","code":"site.flash.deleteBlocked"}""";

	public static final String UNPROCESSABLE_SITE = """
			{"type":"about:blank","title":"Unprocessable Content","status":422,
			 "detail":"Code is already used","instance":"/api/v1/sites","code":"validation.site.code.duplicate",
			 "errors":[{"field":"code","code":"validation.site.code.duplicate","message":"Code is already used"}]}""";

	public static final String UNPROCESSABLE_LOG = """
			{"type":"about:blank","title":"Unprocessable Content","status":422,
			 "detail":"A daily log already exists for this date","instance":"/api/v1/sites/3/daily-logs",
			 "code":"validation.dailyLog.date.duplicate",
			 "errors":[{"field":"logDate","code":"validation.dailyLog.date.duplicate",
			            "message":"A daily log already exists for this date"}]}""";

	public static final String UNPROCESSABLE_CREW = """
			{"type":"about:blank","title":"Unprocessable Content","status":422,
			 "detail":"Select an active crew","instance":"/api/v1/daily-logs/42/work-entries",
			 "code":"validation.crewAssignment.crew.invalid",
			 "errors":[{"field":"crewAssignments[0].crewId","code":"validation.crewAssignment.crew.invalid",
			            "message":"Select an active crew"}]}""";

	public static final String UNPROCESSABLE_SEVERITY = """
			{"type":"about:blank","title":"Unprocessable Content","status":422,
			 "detail":"High and Critical incidents need root cause, witnesses, medical treatment and authority notification.",
			 "instance":"/api/v1/sites/3/incidents","code":"incident.flash.missingFields",
			 "errors":[{"field":"rootCause","code":"validation.incident.rootCause.required","message":"Root cause is required"},
			           {"field":"authorityNotified","code":"validation.incident.authorityNotified.required",
			            "message":"Select Yes or No"}]}""";

	public static final String UNPROCESSABLE_INCIDENT = """
			{"type":"about:blank","title":"Unprocessable Content","status":422,
			 "detail":"The incident cannot be in the future","instance":"/api/v1/sites/3/incidents",
			 "code":"validation.incident.occurredAt.future",
			 "errors":[{"field":"occurredAt","code":"validation.incident.occurredAt.future",
			            "message":"The incident cannot be in the future"}]}""";

	public static final String UNPROCESSABLE_ACTION = """
			{"type":"about:blank","title":"Unprocessable Content","status":422,
			 "detail":"Due date cannot be in the past","instance":"/api/v1/incidents/12/corrective-actions",
			 "code":"validation.correctiveAction.dueDate.past",
			 "errors":[{"field":"dueDate","code":"validation.correctiveAction.dueDate.past",
			            "message":"Due date cannot be in the past"}]}""";

	public static final String FORBIDDEN_INCIDENT_SIGN_OFF = """
			{"type":"about:blank","title":"Forbidden","status":403,
			 "detail":"You do not have permission to open this page or perform this action.",
			 "instance":"/api/v1/incidents/12/sign-off","code":"error.403.body"}""";

	public static final String CONFLICT_INCIDENT_VERSION = """
			{"type":"about:blank","title":"Conflict","status":409,
			 "detail":"This incident was changed by someone else. Reload and try again.",
			 "instance":"/api/v1/incidents/12","code":"incident.flash.conflict"}""";

	public static final String CONFLICT_INCIDENT_OPEN_ACTIONS = """
			{"type":"about:blank","title":"Conflict","status":409,
			 "detail":"Complete every corrective action before closing the incident.",
			 "instance":"/api/v1/incidents/12/close","code":"incident.flash.close.openActions"}""";

	public static final String CONFLICT_INCIDENT_TRANSITION = """
			{"type":"about:blank","title":"Conflict","status":409,
			 "detail":"Only an incident under review can be signed off.",
			 "instance":"/api/v1/incidents/12/sign-off","code":"incident.flash.signOff.notUnderReview"}""";

	public static final String CONFLICT_ACTION_DONE = """
			{"type":"about:blank","title":"Conflict","status":409,
			 "detail":"A completed corrective action can no longer be changed.",
			 "instance":"/api/v1/incidents/12/corrective-actions/5","code":"correctiveAction.flash.done.final"}""";

	public static final String CONFLICT_REOPEN_PENDING = """
			{"type":"about:blank","title":"Conflict","status":409,
			 "detail":"This log already has a reopen request waiting for a decision.",
			 "instance":"/api/v1/daily-logs/42/reopen-requests","code":"reopen.flash.alreadyPending"}""";

	public static final String CONFLICT_REOPEN_DECIDED = """
			{"type":"about:blank","title":"Conflict","status":409,
			 "detail":"This reopen request has already been decided.",
			 "instance":"/api/v1/daily-logs/42/reopen-requests/7/approve","code":"reopen.flash.alreadyDecided"}""";

	private ApiDocs() {
	}

}
