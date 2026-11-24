package hr.algebra.sitelog.user.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.user.repository.AppUserRepository;

/**
 * Login lookup for the form login: e-mail (case-insensitive) -> {@code app_user} with its role,
 * in one query. Spring Boot wires this bean and the {@code PasswordEncoder} bean into a
 * {@code DaoAuthenticationProvider}, which then:
 * <ol>
 * <li>refuses an inactive user ({@code isEnabled() == false}, {@code DisabledException});</li>
 * <li>compares the typed password with the BCrypt hash.</li>
 * </ol>
 * Both failures end on {@code /login?error} with the same generic message, so the page does not
 * reveal which e-mails exist.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

	private final AppUserRepository users;

	public AppUserDetailsService(AppUserRepository users) {
		this.users = users;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		String login = email == null ? "" : email.trim();
		return users.findByEmailIgnoreCase(login)
			.map(AppUserPrincipal::of)
			.orElseThrow(() -> new UsernameNotFoundException("No user with this e-mail"));
	}

}
