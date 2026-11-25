package hr.algebra.sitelog.incident.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import hr.algebra.sitelog.incident.repository.CorrectiveActionRepository;
import hr.algebra.sitelog.incident.repository.IncidentRepository;
import hr.algebra.sitelog.user.service.CurrentUserProvider;

/** The record-level answers behind {@code @incidentAccess} in the {@code @PreAuthorize} rules. */
@ExtendWith(MockitoExtension.class)
class IncidentAccessTests {

	@Mock
	IncidentRepository incidents;

	@Mock
	CorrectiveActionRepository actions;

	@Mock
	CurrentUserProvider currentUser;

	private IncidentAccess access;

	@BeforeEach
	void setUp() {
		access = new IncidentAccess(incidents, actions, currentUser);
		lenient().when(currentUser.currentUserId()).thenReturn(7L);
	}

	@Test
	void ownSite_isTrueForTheSitesManager() {
		when(incidents.existsByIdAndSite_Manager_Id(30L, 7L)).thenReturn(true);

		assertThat(access.isOnOwnSite(30L)).isTrue();
	}

	@Test
	void ownSite_isFalseForAnotherManagersIncident_andTrueForAnUnknownOne() {
		when(incidents.existsByIdAndSite_Manager_Id(30L, 7L)).thenReturn(false);
		when(incidents.existsById(30L)).thenReturn(true);
		when(incidents.existsByIdAndSite_Manager_Id(31L, 7L)).thenReturn(false);
		when(incidents.existsById(31L)).thenReturn(false);

		assertThat(access.isOnOwnSite(30L)).isFalse();
		assertThat(access.isOnOwnSite(31L)).as("unknown id: the service answers 404").isTrue();
		assertThat(access.isOnOwnSite(null)).isFalse();
	}

	@Test
	void lowSeverityOnOwnSite_isFalseForAHighIncidentOfTheSite() {
		when(incidents.existsByIdAndSite_Manager_IdAndSeverityIn(30L, 7L, IncidentAccess.LOW_SEVERITIES))
			.thenReturn(false);
		when(incidents.existsById(30L)).thenReturn(true);

		assertThat(access.isLowSeverityOnOwnSite(30L)).isFalse();
		assertThat(access.isLowSeverityOnOwnSite(null)).isFalse();
	}

	@Test
	void lowSeverityOnOwnSite_isTrueForALowIncidentOfTheSite() {
		when(incidents.existsByIdAndSite_Manager_IdAndSeverityIn(30L, 7L, IncidentAccess.LOW_SEVERITIES))
			.thenReturn(true);

		assertThat(access.isLowSeverityOnOwnSite(30L)).isTrue();
	}

	@Test
	void assignee_isTrueOnlyForTheActionsAssignee() {
		when(actions.existsByIdAndAssignee_Id(1L, 7L)).thenReturn(true);
		when(actions.existsByIdAndAssignee_Id(2L, 7L)).thenReturn(false);
		when(actions.existsById(2L)).thenReturn(true);

		assertThat(access.isAssignee(1L)).isTrue();
		assertThat(access.isAssignee(2L)).isFalse();
		assertThat(access.isAssignee(null)).isFalse();
	}

}
