package hr.algebra.sitelog.user.service;

import java.util.Optional;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

/**
 * Who is making the current request. Services ask this interface instead of reading the
 * security context directly, so they stay plain Java in unit tests (a mock returns the user).
 *
 * <p>Implementation: {@link SecurityContextCurrentUserProvider} (the user signed in with Spring
 * Security).
 */
public interface CurrentUserProvider {

	/** The signed-in user, or empty for an anonymous request (e.g. the login page). */
	Optional<CurrentUser> findCurrentUser();

	/** The signed-in user; an anonymous caller is a programming error (the URL rules require a login). */
	default CurrentUser currentUser() {
		return findCurrentUser()
			.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("No signed-in user"));
	}

	default Long currentUserId() {
		return currentUser().id();
	}

}
