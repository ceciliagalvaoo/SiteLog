package hr.algebra.sitelog.user.service;

import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.user.repository.AppUserRepository;
import hr.algebra.sitelog.user.security.AppUserPrincipal;

/**
 * The current user from Spring Security's {@link SecurityContextHolder} (filled from the HTTP
 * session by the security filter chain, one context per request thread).
 *
 * <ul>
 * <li>{@link #currentUserId()} reads the id from the principal - no query (used by every write
 * that records "who", e.g. the sign-off).</li>
 * <li>{@link #findCurrentUser()} reloads name and role by id (one query with the role joined),
 * so the top bar shows a name changed on the profile page without logging in again.</li>
 * <li>A principal that is not an {@link AppUserPrincipal} (e.g. {@code @WithMockUser} in a
 * test) is looked up by its name, the e-mail.</li>
 * </ul>
 */
@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

	private final AppUserRepository users;

	public SecurityContextCurrentUserProvider(AppUserRepository users) {
		this.users = users;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<CurrentUser> findCurrentUser() {
		Authentication authentication = signedIn();
		if (authentication == null) {
			return Optional.empty();
		}
		if (authentication.getPrincipal() instanceof AppUserPrincipal principal) {
			return users.findWithRoleById(principal.getId()).map(CurrentUser::of);
		}
		return users.findByEmailIgnoreCase(authentication.getName()).map(CurrentUser::of);
	}

	@Override
	public Long currentUserId() {
		Authentication authentication = signedIn();
		if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
			return principal.getId();
		}
		return currentUser().id();
	}

	private static Authentication signedIn() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			return null;
		}
		return authentication;
	}

}
