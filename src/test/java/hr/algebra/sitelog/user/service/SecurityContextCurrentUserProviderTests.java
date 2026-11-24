package hr.algebra.sitelog.user.service;

import static hr.algebra.sitelog.support.TestEntities.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.repository.AppUserRepository;
import hr.algebra.sitelog.user.security.AppUserPrincipal;

@ExtendWith(MockitoExtension.class)
class SecurityContextCurrentUserProviderTests {

	@Mock
	AppUserRepository users;

	SecurityContextCurrentUserProvider provider;

	@BeforeEach
	void setUp() {
		provider = new SecurityContextCurrentUserProvider(users);
	}

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	private static void signIn(AppUserPrincipal principal) {
		SecurityContextHolder.getContext().setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
	}

	private static AppUserPrincipal principal(Long id, RoleName role) {
		return new AppUserPrincipal(id, "marko.kovacevic@sitelog.test", null, "Marko Kovacevic", role, "hr", true);
	}

	@Test
	void signedInUser_isReloadedByIdWithFreshNameAndRole() {
		signIn(principal(7L, RoleName.SITE_MANAGER));
		when(users.findWithRoleById(7L))
			.thenReturn(Optional.of(user(7L, "Marko Kovacevic Jr", RoleName.SITE_MANAGER, true)));

		CurrentUser current = provider.currentUser();

		assertThat(current.id()).isEqualTo(7L);
		assertThat(current.fullName()).isEqualTo("Marko Kovacevic Jr");
		assertThat(current.role()).isEqualTo(RoleName.SITE_MANAGER);
		assertThat(current.hasRole(RoleName.SITE_MANAGER)).isTrue();
		assertThat(current.hasRole(RoleName.ADMIN)).isFalse();
	}

	@Test
	void currentUserId_comesFromThePrincipalWithoutAQuery() {
		signIn(principal(7L, RoleName.SITE_MANAGER));

		assertThat(provider.currentUserId()).isEqualTo(7L);
		verifyNoInteractions(users);
	}

	@Test
	void otherPrincipal_isLookedUpByEmail() {
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin@sitelog.test",
				null, AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
		when(users.findByEmailIgnoreCase("admin@sitelog.test"))
			.thenReturn(Optional.of(user(1L, "Ana Horvat", RoleName.ADMIN, true)));

		assertThat(provider.currentUserId()).isEqualTo(1L);
		assertThat(provider.findCurrentUser()).map(CurrentUser::role).contains(RoleName.ADMIN);
	}

	@Test
	void anonymousRequest_hasNoCurrentUser() {
		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
				AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

		assertThat(provider.findCurrentUser()).isEmpty();
		assertThatThrownBy(provider::currentUser).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
		assertThatThrownBy(provider::currentUserId).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
	}

	@Test
	void noAuthenticationAtAll_hasNoCurrentUser() {
		assertThat(provider.findCurrentUser()).isEmpty();
		verifyNoInteractions(users);
	}

	@Test
	void initials_takeFirstLettersOfAtMostTwoNames() {
		assertThat(new CurrentUser(1L, "a@b", "marko  kovačević horvat", RoleName.ADMIN).initials()).isEqualTo("MK");
		assertThat(new CurrentUser(1L, "a@b", " Ana ", RoleName.ADMIN).initials()).isEqualTo("A");
	}

	@Test
	void principal_exposesRoleAuthorityAndErasesThePasswordHash() {
		AppUserPrincipal principal = new AppUserPrincipal(3L, "a@b", "$2a$10$hash", "Ana", RoleName.INSPECTOR, "en",
				false);

		assertThat(principal.getAuthorities()).extracting(Object::toString).isEqualTo(List.of("ROLE_INSPECTOR"));
		assertThat(principal.isEnabled()).isFalse();
		assertThat(principal.getUsername()).isEqualTo("a@b");
		principal.eraseCredentials();
		assertThat(principal.getPassword()).isNull();
		assertThat(principal).isEqualTo(principal(3L, RoleName.ADMIN)).hasSameHashCodeAs(principal(3L, RoleName.ADMIN));
	}

}
