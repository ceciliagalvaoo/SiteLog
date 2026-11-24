package hr.algebra.sitelog.user.security;

import static hr.algebra.sitelog.support.TestEntities.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTests {

	@Mock
	AppUserRepository users;

	@InjectMocks
	AppUserDetailsService service;

	@Test
	void knownEmail_givesPrincipalWithRoleAndHash() {
		when(users.findByEmailIgnoreCase("ana.horvat@sitelog.test"))
			.thenReturn(Optional.of(user(1L, "Ana Horvat", RoleName.ADMIN, true)));

		UserDetails details = service.loadUserByUsername("  ana.horvat@sitelog.test ");

		assertThat(details).isInstanceOf(AppUserPrincipal.class);
		assertThat(details.getUsername()).isEqualTo("ana.horvat@sitelog.test");
		assertThat(details.getPassword()).isEqualTo("{noop}x");
		assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
		assertThat(details.isEnabled()).isTrue();
		assertThat(((AppUserPrincipal) details).getId()).isEqualTo(1L);
	}

	@Test
	void inactiveUser_isDisabled() {
		when(users.findByEmailIgnoreCase("ana.horvat@sitelog.test"))
			.thenReturn(Optional.of(user(1L, "Ana Horvat", RoleName.ADMIN, false)));

		assertThat(service.loadUserByUsername("ana.horvat@sitelog.test").isEnabled()).isFalse();
	}

	@Test
	void unknownEmail_isRefused() {
		when(users.findByEmailIgnoreCase("nobody@sitelog.test")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.loadUserByUsername("nobody@sitelog.test"))
			.isInstanceOf(UsernameNotFoundException.class);
	}

	@Test
	void nullEmail_isRefused() {
		when(users.findByEmailIgnoreCase("")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.loadUserByUsername(null)).isInstanceOf(UsernameNotFoundException.class);
	}

}
