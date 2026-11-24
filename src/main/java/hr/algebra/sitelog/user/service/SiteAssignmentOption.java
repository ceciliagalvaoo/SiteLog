package hr.algebra.sitelog.user.service;

/**
 * One checkbox of the "Assigned sites" card of the user form: every site, with its current
 * manager; {@code assigned} when it is the edited user.
 */
public record SiteAssignmentOption(Long id, String code, String name, String managerName, boolean assigned) {
}
