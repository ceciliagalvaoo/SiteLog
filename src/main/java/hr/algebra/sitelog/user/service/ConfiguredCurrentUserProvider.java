package hr.algebra.sitelog.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.user.domain.AppUser;
import hr.algebra.sitelog.user.repository.AppUserRepository;

/**
 * Temporary (milestone M2): the "current user" is the seed user named by
 * {@code sitelog.current-user.email} (default: the demo site manager from V4). There is no real
 * authentication of domain users yet; M3 replaces this bean with one that reads the
 * authenticated principal, and removes the property.
 */
@Component
public class ConfiguredCurrentUserProvider implements CurrentUserProvider {

	private final AppUserRepository users;

	private final String email;

	public ConfiguredCurrentUserProvider(AppUserRepository users,
			@Value("${sitelog.current-user.email}") String email) {
		this.users = users;
		this.email = email;
	}

	@Override
	@Transactional(readOnly = true)
	public CurrentUser currentUser() {
		AppUser user = users.findByEmailIgnoreCase(email)
			.orElseThrow(() -> new IllegalStateException("Configured current user not found: " + email));
		return new CurrentUser(user.getId(), user.getEmail(), user.getFullName(), user.getRole().getName());
	}

}
