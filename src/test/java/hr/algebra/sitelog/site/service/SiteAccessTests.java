package hr.algebra.sitelog.site.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import hr.algebra.sitelog.dailylog.repository.DailyLogRepository;
import hr.algebra.sitelog.site.repository.SiteRepository;
import hr.algebra.sitelog.user.service.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
class SiteAccessTests {

	private static final Long ME = 7L;

	@Mock
	SiteRepository sites;

	@Mock
	DailyLogRepository dailyLogs;

	@Mock
	CurrentUserProvider currentUser;

	SiteAccess access;

	@BeforeEach
	void setUp() {
		access = new SiteAccess(sites, dailyLogs, currentUser);
	}

	@Test
	void ownSite_isManageable() {
		when(currentUser.currentUserId()).thenReturn(ME);
		when(sites.existsByIdAndManager_Id(1L, ME)).thenReturn(true);

		assertThat(access.canManageSite(1L)).isTrue();
	}

	@Test
	void someoneElsesSite_isNot() {
		when(currentUser.currentUserId()).thenReturn(ME);
		when(sites.existsByIdAndManager_Id(2L, ME)).thenReturn(false);
		when(sites.existsById(2L)).thenReturn(true);

		assertThat(access.canManageSite(2L)).isFalse();
	}

	@Test
	void unknownSite_isLeftToTheServiceFor404() {
		when(currentUser.currentUserId()).thenReturn(ME);
		when(sites.existsByIdAndManager_Id(99L, ME)).thenReturn(false);
		when(sites.existsById(99L)).thenReturn(false);

		assertThat(access.canManageSite(99L)).isTrue();
	}

	@Test
	void logOfOwnSite_isManageable() {
		when(currentUser.currentUserId()).thenReturn(ME);
		when(dailyLogs.existsByIdAndSite_Manager_Id(10L, ME)).thenReturn(true);

		assertThat(access.canManageLog(10L)).isTrue();
	}

	@Test
	void logOfAnotherSite_isNot() {
		when(currentUser.currentUserId()).thenReturn(ME);
		when(dailyLogs.existsByIdAndSite_Manager_Id(11L, ME)).thenReturn(false);
		when(dailyLogs.existsById(11L)).thenReturn(true);

		assertThat(access.canManageLog(11L)).isFalse();
	}

	@Test
	void unknownLog_isLeftToTheServiceFor404() {
		when(currentUser.currentUserId()).thenReturn(ME);
		when(dailyLogs.existsByIdAndSite_Manager_Id(99L, ME)).thenReturn(false);
		when(dailyLogs.existsById(99L)).thenReturn(false);

		assertThat(access.canManageLog(99L)).isTrue();
	}

	@Test
	void nullIds_areRefused() {
		assertThat(access.canManageSite(null)).isFalse();
		assertThat(access.canManageLog(null)).isFalse();
		verifyNoInteractions(sites, dailyLogs, currentUser);
	}

}
