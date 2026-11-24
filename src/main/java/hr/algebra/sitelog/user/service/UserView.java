package hr.algebra.sitelog.user.service;

import java.util.List;

import hr.algebra.sitelog.user.domain.RoleName;

/**
 * A user with the sites he or she manages: the Admin's user form and the user's own profile page.
 * {@code self} is true when it is the signed-in user (the form then hides "deactivate" and the
 * role select).
 */
public record UserView(Long id, String email, String fullName, RoleName role, String preferredLanguage,
		boolean active, boolean self, List<ManagedSite> managedSites) {

	public boolean siteManager() {
		return role == RoleName.SITE_MANAGER;
	}

}
