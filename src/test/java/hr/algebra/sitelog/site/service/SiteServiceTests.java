package hr.algebra.sitelog.site.service;

import static hr.algebra.sitelog.support.TestEntities.TODAY;
import static hr.algebra.sitelog.support.TestEntities.manager;
import static hr.algebra.sitelog.support.TestEntities.site;
import static hr.algebra.sitelog.support.TestEntities.user;
import static hr.algebra.sitelog.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.repository.DailyLogRepository;
import hr.algebra.sitelog.incident.domain.CorrectiveActionStatus;
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.repository.CorrectiveActionRepository;
import hr.algebra.sitelog.incident.repository.IncidentRepository;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.site.repository.SiteRepository;
import hr.algebra.sitelog.support.TestEntities;
import hr.algebra.sitelog.user.domain.AppUser;
import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class SiteServiceTests {

	private static final Pageable PAGE = PageRequest.of(0, 20);

	@Mock
	SiteRepository sites;

	@Mock
	AppUserRepository users;

	@Mock
	DailyLogRepository dailyLogs;

	@Mock
	IncidentRepository incidents;

	@Mock
	CorrectiveActionRepository correctiveActions;

	SiteService service;

	AppUser manager;

	@BeforeEach
	void setUp() {
		service = new SiteService(sites, users, dailyLogs, incidents, correctiveActions, TestEntities.fixedClock());
		manager = manager(7L);
	}

	private Site siteEnding(Long id, LocalDate endDate) {
		Site site = site(id, "ZG-" + id, manager);
		site.setEndDate(endDate);
		return site;
	}

	private SiteCommand command(String code, Long managerId) {
		return new SiteCommand(code, "Tower A", "Vukovarska 10", "Office block", LocalDate.of(2026, 3, 1),
				LocalDate.of(2027, 6, 30), managerId);
	}

	@Test
	void command_normalisesCodeToTrimmedUpperCaseAndBlankDescriptionToNull() {
		SiteCommand command = new SiteCommand("  zg-01 ", " Tower ", " Street 1 ", "   ", null, null, 1L);

		assertThat(command.code()).isEqualTo("ZG-01");
		assertThat(command.name()).isEqualTo("Tower");
		assertThat(command.address()).isEqualTo("Street 1");
		assertThat(command.description()).isNull();
	}

	@Test
	void list_withoutFilter_marksSiteFinishedOnlyWhenEndDateIsTodayOrPast() {
		when(sites.findAllWithManager(PAGE)).thenReturn(new PageImpl<>(List.of(siteEnding(1L, null),
				siteEnding(2L, TODAY.minusDays(1)), siteEnding(3L, TODAY), siteEnding(4L, TODAY.plusDays(1)))));

		List<SiteListItem> items = service.list(null, PAGE).getContent();

		assertThat(items).extracting(SiteListItem::id, SiteListItem::finished)
			.containsExactly(tuple(1L, false), tuple(2L, true),
					tuple(3L, true), tuple(4L, false));
		assertThat(items).extracting(SiteListItem::managerName).containsOnly("Marko Kovacevic");
		verify(sites, never()).searchWithManager(any(), any());
	}

	@Test
	void list_withBlankFilter_usesUnfilteredQuery() {
		when(sites.findAllWithManager(PAGE)).thenReturn(new PageImpl<>(List.of()));

		assertThat(service.list("   ", PAGE).getContent()).isEmpty();
		verify(sites, never()).searchWithManager(any(), any());
	}

	@Test
	void list_withFilter_searchesWithTrimmedText() {
		when(sites.searchWithManager("tower", PAGE)).thenReturn(new PageImpl<>(List.of(siteEnding(5L, null))));

		List<SiteListItem> items = service.list("  tower ", PAGE).getContent();

		assertThat(items).extracting(SiteListItem::code).containsExactly("ZG-5");
		verify(sites, never()).findAllWithManager(any());
	}

	@Test
	void listByStatus_withAll_isTheUnfilteredList() {
		when(sites.findAllWithManager(PAGE)).thenReturn(new PageImpl<>(List.of(siteEnding(1L, null))));

		assertThat(service.list(null, SiteStatusFilter.ALL, PAGE).getContent()).hasSize(1);
		verify(sites, never()).searchByStatusWithManager(any(), any(), any(), any());
	}

	@Test
	void listByStatus_withFinished_queriesWithTrimmedTextStatusNameAndClockToday() {
		when(sites.searchByStatusWithManager("tower", "FINISHED", TODAY, PAGE))
			.thenReturn(new PageImpl<>(List.of(siteEnding(2L, TODAY))));

		List<SiteListItem> items = service.list(" tower ", SiteStatusFilter.FINISHED, PAGE).getContent();

		assertThat(items).extracting(SiteListItem::finished).containsExactly(true);
	}

	@Test
	void listByStatus_withActiveAndNoText_passesEmptyText() {
		when(sites.searchByStatusWithManager("", "ACTIVE", TODAY, PAGE))
			.thenReturn(new PageImpl<>(List.of(siteEnding(3L, TODAY.plusDays(1)))));

		assertThat(service.list(null, SiteStatusFilter.ACTIVE, PAGE).getContent()).extracting(SiteListItem::finished)
			.containsExactly(false);
	}

	@Test
	void statusFilter_fromParam_isLenient() {
		assertThat(SiteStatusFilter.fromParam(" finished ")).isEqualTo(SiteStatusFilter.FINISHED);
		assertThat(SiteStatusFilter.fromParam("active")).isEqualTo(SiteStatusFilter.ACTIVE);
		assertThat(SiteStatusFilter.fromParam(null)).isEqualTo(SiteStatusFilter.ALL);
		assertThat(SiteStatusFilter.fromParam("  ")).isEqualTo(SiteStatusFilter.ALL);
		assertThat(SiteStatusFilter.fromParam("bogus")).isEqualTo(SiteStatusFilter.ALL);
	}

	@Test
	void get_returnsSiteWithKpisAndSumsDraftAndReopenedAsAwaitingSignOff() {
		Site site = siteEnding(3L, TODAY.minusMonths(1));
		site.setDescription("Office block");
		when(sites.findWithManagerById(3L)).thenReturn(Optional.of(site));
		when(dailyLogs.countBySite_Id(3L)).thenReturn(12L);
		when(dailyLogs.countBySite_IdAndStatus(3L, LogStatus.DRAFT)).thenReturn(2L);
		when(dailyLogs.countBySite_IdAndStatus(3L, LogStatus.REOPENED)).thenReturn(1L);
		when(incidents.countBySite_IdAndStatusIn(3L, EnumSet.of(IncidentStatus.REPORTED, IncidentStatus.UNDER_REVIEW)))
			.thenReturn(4L);
		when(correctiveActions.countByIncident_Site_IdAndStatusIn(3L,
				EnumSet.of(CorrectiveActionStatus.OPEN, CorrectiveActionStatus.OVERDUE)))
			.thenReturn(5L);
		when(dailyLogs.findLastLogDateBySiteId(3L)).thenReturn(Optional.of(TODAY.minusDays(40)));

		SiteDetails details = service.get(3L);

		assertThat(details.code()).isEqualTo("ZG-3");
		assertThat(details.managerId()).isEqualTo(7L);
		assertThat(details.managerName()).isEqualTo("Marko Kovacevic");
		assertThat(details.description()).isEqualTo("Office block");
		assertThat(details.finished()).isTrue();
		assertThat(details.dailyLogCount()).isEqualTo(12L);
		assertThat(details.logsAwaitingSignOff()).isEqualTo(3L);
		assertThat(details.openIncidentCount()).isEqualTo(4L);
		assertThat(details.openCorrectiveActionCount()).isEqualTo(5L);
		assertThat(details.lastLogDate()).isEqualTo(TODAY.minusDays(40));
	}

	@Test
	void get_withoutLogs_hasNullLastLogDate() {
		when(sites.findWithManagerById(3L)).thenReturn(Optional.of(siteEnding(3L, null)));
		when(dailyLogs.findLastLogDateBySiteId(3L)).thenReturn(Optional.empty());

		SiteDetails details = service.get(3L);

		assertThat(details.lastLogDate()).isNull();
		assertThat(details.finished()).isFalse();
		assertThat(details.dailyLogCount()).isZero();
	}

	@Test
	void get_whenSiteMissing_throwsNotFoundException() {
		when(sites.findWithManagerById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(99L)).isInstanceOf(NotFoundException.class)
			.hasMessageContaining("Site 99");
	}

	@Test
	void getSummary_mapsSiteWithoutKpis() {
		when(sites.findWithManagerById(3L)).thenReturn(Optional.of(siteEnding(3L, TODAY)));

		SiteListItem summary = service.getSummary(3L);

		assertThat(summary.code()).isEqualTo("ZG-3");
		assertThat(summary.finished()).isTrue();
	}

	@Test
	void getSummary_whenSiteMissing_throwsNotFoundException() {
		when(sites.findWithManagerById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getSummary(99L)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void managerOptions_listsActiveSiteManagers() {
		when(users.findByRole_NameAndActiveTrueOrderByFullNameAsc(RoleName.SITE_MANAGER))
			.thenReturn(List.of(manager, user(8L, "Ana Horvat", RoleName.SITE_MANAGER, true)));

		assertThat(service.managerOptions()).extracting(o -> o.id() + ":" + o.fullName())
			.containsExactly("7:Marko Kovacevic", "8:Ana Horvat");
	}

	@Test
	void create_withValidCommand_savesSiteWithAllFields() {
		when(sites.existsByCodeIgnoreCase("ZG-01")).thenReturn(false);
		when(users.findById(7L)).thenReturn(Optional.of(manager));
		when(sites.save(any(Site.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 42L));

		Long id = service.create(command("zg-01", 7L));

		assertThat(id).isEqualTo(42L);
		ArgumentCaptor<Site> saved = ArgumentCaptor.forClass(Site.class);
		verify(sites).save(saved.capture());
		assertThat(saved.getValue().getCode()).isEqualTo("ZG-01");
		assertThat(saved.getValue().getName()).isEqualTo("Tower A");
		assertThat(saved.getValue().getDescription()).isEqualTo("Office block");
		assertThat(saved.getValue().getEndDate()).isEqualTo(LocalDate.of(2027, 6, 30));
		assertThat(saved.getValue().getManager()).isSameAs(manager);
	}

	@Test
	void create_whenCodeAlreadyExists_throwsInvalidFieldOnCodeAndSavesNothing() {
		when(sites.existsByCodeIgnoreCase("ZG-01")).thenReturn(true);

		assertThatThrownBy(() -> service.create(command("ZG-01", 7L)))
			.isInstanceOfSatisfying(InvalidFieldException.class, e -> {
				assertThat(e.getField()).isEqualTo("code");
				assertThat(e.getMessageKey()).isEqualTo(SiteService.CODE_DUPLICATE);
			});
		verify(sites, never()).save(any());
	}

	@Test
	void create_whenManagerIsInactive_throwsInvalidFieldOnManagerId() {
		when(sites.existsByCodeIgnoreCase("ZG-01")).thenReturn(false);
		when(users.findById(8L)).thenReturn(Optional.of(user(8L, "Old Manager", RoleName.SITE_MANAGER, false)));

		assertManagerRejected(8L);
	}

	@Test
	void create_whenManagerHasAnotherRole_throwsInvalidFieldOnManagerId() {
		when(sites.existsByCodeIgnoreCase("ZG-01")).thenReturn(false);
		when(users.findById(9L)).thenReturn(Optional.of(user(9L, "Iva Inspector", RoleName.INSPECTOR, true)));

		assertManagerRejected(9L);
	}

	@Test
	void create_whenManagerDoesNotExist_throwsInvalidFieldOnManagerId() {
		when(sites.existsByCodeIgnoreCase("ZG-01")).thenReturn(false);
		when(users.findById(404L)).thenReturn(Optional.empty());

		assertManagerRejected(404L);
	}

	private void assertManagerRejected(Long managerId) {
		assertThatThrownBy(() -> service.create(command("ZG-01", managerId)))
			.isInstanceOfSatisfying(InvalidFieldException.class, e -> {
				assertThat(e.getField()).isEqualTo("managerId");
				assertThat(e.getMessageKey()).isEqualTo(SiteService.MANAGER_INVALID);
			});
		verify(sites, never()).save(any());
	}

	@Test
	void update_withValidCommand_changesTheManagedSite() {
		Site site = siteEnding(3L, null);
		AppUser newManager = user(8L, "Ana Horvat", RoleName.SITE_MANAGER, true);
		when(sites.findById(3L)).thenReturn(Optional.of(site));
		when(sites.existsByCodeIgnoreCaseAndIdNot("ZG-NEW", 3L)).thenReturn(false);
		when(users.findById(8L)).thenReturn(Optional.of(newManager));

		service.update(3L, command("zg-new", 8L));

		assertThat(site.getCode()).isEqualTo("ZG-NEW");
		assertThat(site.getName()).isEqualTo("Tower A");
		assertThat(site.getAddress()).isEqualTo("Vukovarska 10");
		assertThat(site.getStartDate()).isEqualTo(LocalDate.of(2026, 3, 1));
		assertThat(site.getEndDate()).isEqualTo(LocalDate.of(2027, 6, 30));
		assertThat(site.getManager()).isSameAs(newManager);
	}

	@Test
	void update_whenCodeBelongsToAnotherSite_throwsInvalidFieldAndKeepsOldCode() {
		Site site = siteEnding(3L, null);
		when(sites.findById(3L)).thenReturn(Optional.of(site));
		when(sites.existsByCodeIgnoreCaseAndIdNot("ZG-9", 3L)).thenReturn(true);

		assertThatThrownBy(() -> service.update(3L, command("ZG-9", 7L)))
			.isInstanceOfSatisfying(InvalidFieldException.class, e -> assertThat(e.getField()).isEqualTo("code"));
		assertThat(site.getCode()).isEqualTo("ZG-3");
	}

	@Test
	void update_whenSiteMissing_throwsNotFoundException() {
		when(sites.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(99L, command("ZG-1", 7L))).isInstanceOf(NotFoundException.class);
	}

	@Test
	void delete_whenSiteHasDailyLogs_throwsDeletionNotAllowedAndKeepsSite() {
		when(sites.findById(3L)).thenReturn(Optional.of(siteEnding(3L, null)));
		when(dailyLogs.existsBySite_Id(3L)).thenReturn(true);

		assertDeleteBlocked();
	}

	@Test
	void delete_whenSiteHasIncidents_throwsDeletionNotAllowedAndKeepsSite() {
		when(sites.findById(3L)).thenReturn(Optional.of(siteEnding(3L, null)));
		when(dailyLogs.existsBySite_Id(3L)).thenReturn(false);
		when(incidents.existsBySite_Id(3L)).thenReturn(true);

		assertDeleteBlocked();
	}

	@Test
	void delete_whenSiteHasIncidentDrafts_throwsDeletionNotAllowedAndKeepsSite() {
		when(sites.findById(3L)).thenReturn(Optional.of(siteEnding(3L, null)));
		when(dailyLogs.existsBySite_Id(3L)).thenReturn(false);
		when(incidents.existsBySite_Id(3L)).thenReturn(false);
		when(sites.existsIncidentDraftBySiteId(3L)).thenReturn(true);

		assertDeleteBlocked();
	}

	private void assertDeleteBlocked() {
		assertThatThrownBy(() -> service.delete(3L))
			.isInstanceOfSatisfying(DeletionNotAllowedException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(SiteService.DELETE_BLOCKED));
		verify(sites, never()).delete(any());
	}

	@Test
	void delete_whenSiteHasNoRecords_deletesIt() {
		Site site = siteEnding(3L, null);
		when(sites.findById(3L)).thenReturn(Optional.of(site));

		service.delete(3L);

		verify(sites).delete(site);
	}

	@Test
	void delete_whenSiteMissing_throwsNotFoundException() {
		when(sites.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(NotFoundException.class);
		verify(sites, never()).delete(any());
	}

}
