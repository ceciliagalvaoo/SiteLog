package hr.algebra.sitelog.support;

import java.util.function.Supplier;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * Runs a piece of a test as another seed user (for example the Admin creating the site that the
 * site manager of the test then works on), then restores the previous security context.
 */
public final class RunAs {

	private RunAs() {
	}

	public static <T> T user(UserDetailsService users, String email, Supplier<T> action) {
		SecurityContext previous = SecurityContextHolder.getContext();
		UserDetails details = users.loadUserByUsername(email);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities()));
		SecurityContextHolder.setContext(context);
		try {
			return action.get();
		}
		finally {
			SecurityContextHolder.setContext(previous);
		}
	}

	public static void user(UserDetailsService users, String email, Runnable action) {
		user(users, email, () -> {
			action.run();
			return null;
		});
	}

}
