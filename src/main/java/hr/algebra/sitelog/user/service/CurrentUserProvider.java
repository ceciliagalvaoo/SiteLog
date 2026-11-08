package hr.algebra.sitelog.user.service;

/**
 * Who is making the current request. Services ask this interface instead of reading the
 * security context directly, so the source of the user can change without touching them.
 *
 * <p>M2: {@link ConfiguredCurrentUserProvider} returns a fixed seed user (no real login yet).
 * M3: replaced by an implementation backed by Spring Security's {@code SecurityContextHolder}.
 */
public interface CurrentUserProvider {

	CurrentUser currentUser();

	default Long currentUserId() {
		return currentUser().id();
	}

}
