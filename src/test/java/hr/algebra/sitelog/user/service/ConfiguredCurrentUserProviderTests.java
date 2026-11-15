package hr.algebra.sitelog.user.service;

import static hr.algebra.sitelog.support.TestEntities.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class ConfiguredCurrentUserProviderTests {

	private static final String EMAIL = "manager@sitelog.test";

	@Mock
	AppUserRepository users;

	ConfiguredCurrentUserProvider provider;

	@BeforeEach
	void setUp() {
		provider = new ConfiguredCurrentUserProvider(users, EMAIL);
	}

	@Test
	void currentUser_returnsSnapshotOfTheConfiguredSeedUser() {
		when(users.findByEmailIgnoreCase(EMAIL))
			.thenReturn(Optional.of(user(7L, "Marko Kovacevic", RoleName.SITE_MANAGER, true)));

		CurrentUser current = provider.currentUser();

		assertThat(current.id()).isEqualTo(7L);
		assertThat(current.fullName()).isEqualTo("Marko Kovacevic");
		assertThat(current.role()).isEqualTo(RoleName.SITE_MANAGER);
		assertThat(provider.currentUserId()).isEqualTo(7L);
	}

	@Test
	void currentUser_whenConfiguredUserIsMissing_failsFast() {
		when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

		assertThatThrownBy(provider::currentUser).isInstanceOf(IllegalStateException.class).hasMessageContaining(EMAIL);
	}

	@Test
	void initials_takeFirstLettersOfAtMostTwoNames() {
		assertThat(new CurrentUser(1L, "a@b", "marko  kovačević horvat", RoleName.ADMIN).initials()).isEqualTo("MK");
		assertThat(new CurrentUser(1L, "a@b", " Ana ", RoleName.ADMIN).initials()).isEqualTo("A");
	}

}
