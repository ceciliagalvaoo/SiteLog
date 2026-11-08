package hr.algebra.sitelog.notification.domain;

/** What produced the notification. Values match {@code ck_notification_type} in V6. */
public enum NotificationType {
	/** Scheduled job: a corrective action passed its due date. Sent to the assignee. */
	CORRECTIVE_ACTION_OVERDUE,
	/** Scheduled job: a daily log is still unsigned more than 24 h after its day. Sent to the site manager. */
	DAILY_LOG_UNSIGNED,
	/** Domain event: a site manager asked to reopen a signed-off log. Sent to safety supervisors. */
	REOPEN_REQUESTED,
	/** Domain event: a HIGH or CRITICAL incident was reported. Sent to safety supervisors. */
	HIGH_SEVERITY_INCIDENT
}
