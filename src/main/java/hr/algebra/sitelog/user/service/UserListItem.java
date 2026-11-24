package hr.algebra.sitelog.user.service;

import hr.algebra.sitelog.user.domain.AppUser;
import hr.algebra.sitelog.user.domain.RoleName;

/** One row of the user administration list. */
public record UserListItem(Long id, String email, String fullName, RoleName role, String preferredLanguage,
		boolean active) {

	static UserListItem of(AppUser user) {
		return new UserListItem(user.getId(), user.getEmail(), user.getFullName(), user.getRole().getName(),
				user.getPreferredLanguage(), user.isActive());
	}

}
