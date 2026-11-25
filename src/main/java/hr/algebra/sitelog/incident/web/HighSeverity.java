package hr.algebra.sitelog.incident.web;

/**
 * Bean Validation group of the fields a HIGH / CRITICAL incident requires (AR2). The incident
 * controller validates it in addition to the default group only when the chosen severity needs
 * it ({@link IncidentForm#requiresExtendedFields()}).
 */
public interface HighSeverity {
}
