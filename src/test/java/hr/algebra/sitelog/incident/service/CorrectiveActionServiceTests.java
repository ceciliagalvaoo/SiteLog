package hr.algebra.sitelog.incident.service;

import static hr.algebra.sitelog.incident.service.IncidentFixtures.action;
import static hr.algebra.sitelog.incident.service.IncidentFixtures.incident;
import static hr.algebra.sitelog.support.TestEntities.NOW;
import static hr.algebra.sitelog.support.TestEntities.TODAY;
import static hr.algebra.sitelog.support.TestEntities.manager;
import static hr.algebra.sitelog.support.TestEntities.site;
import static hr.algebra.sitelog.support.TestEntities.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.incident.domain.CorrectiveAction;
import hr.algebra.sitelog.incident.domain.CorrectiveActionStatus;
import hr.algebra.sitelog.incident.domain.Incident;
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.domain.Severity;
import hr.algebra.sitelog.incident.repository.IncidentRepository;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.support.TestEntities;
import hr.algebra.sitelog.user.domain.AppUser;
import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.repository.AppUserRepository;

/** Corrective actions: due date, assignee, DONE is final, closed incident, version of the incident. */
@ExtendWith(MockitoExtension.class)
class CorrectiveActionServiceTests {

	private static final Long INCIDENT_ID = 30L;

	@Mock
	IncidentRepository incidents;

	@Mock
	AppUserRepository users;

	private CorrectiveActionService service;

	private final AppUser manager = manager(7L);

	private final AppUser supervisor = user(9L, "Ivana Babic", RoleName.SAFETY_SUPERVISOR, true);

	private final Site site = site(1L, "ZG-01", manager);

	private Incident incident;

	@BeforeEach
	void setUp() {
		service = new CorrectiveActionService(incidents, users, TestEntities.fixedClock());
		incident = incident(INCIDENT_ID, site, Severity.HIGH, IncidentStatus.SIGNED_OFF, manager, 5);
		lenient().when(incidents.findForChildUpdateById(INCIDENT_ID)).thenReturn(Optional.of(incident));
		lenient().when(incidents.findDetailById(INCIDENT_ID)).thenReturn(Optional.of(incident));
	}

	private static CorrectiveActionCommand command(Long assigneeId, java.time.LocalDate due) {
		return new CorrectiveActionCommand("  Install toe boards  ", assigneeId, due);
	}

	@Test
	void create_addsAnOpenActionToTheIncidentLoadedWithForcedVersionIncrement() {
		when(users.findWithRoleById(7L)).thenReturn(Optional.of(manager));

		service.create(INCIDENT_ID, 5L, command(7L, TODAY.plusDays(3)));

		assertThat(incident.getCorrectiveActions()).singleElement().satisfies(action -> {
			assertThat(action.getDescription()).isEqualTo("Install toe boards");
			assertThat(action.getStatus()).isEqualTo(CorrectiveActionStatus.OPEN);
			assertThat(action.getAssignee()).isSameAs(manager);
			assertThat(action.getIncident()).isSameAs(incident);
		});
		InOrder order = inOrder(incidents);
		order.verify(incidents).findForChildUpdateById(INCIDENT_ID);
		order.verify(incidents).findDetailById(INCIDENT_ID);
		order.verify(incidents).flush();
	}

	@Test
	void create_withoutAssignee_isAllowed() {
		service.create(INCIDENT_ID, 5L, command(null, TODAY));

		assertThat(incident.getCorrectiveActions()).singleElement()
			.satisfies(action -> assertThat(action.getAssignee()).isNull());
	}

	@Test
	void create_withADueDateInThePast_isAFieldError() {
		assertThatThrownBy(() -> service.create(INCIDENT_ID, 5L, command(null, TODAY.minusDays(1))))
			.isInstanceOfSatisfying(InvalidFieldException.class, e -> {
				assertThat(e.getField()).isEqualTo("dueDate");
				assertThat(e.getMessageKey()).isEqualTo(CorrectiveActionService.DUE_DATE_PAST);
			});
		assertThat(incident.getCorrectiveActions()).isEmpty();
	}

	@Test
	void create_withAnInactiveOrWrongRoleAssignee_isAFieldError() {
		AppUser inspector = user(11L, "Tomislav Novak", RoleName.INSPECTOR, true);
		AppUser inactive = user(12L, "Old Manager", RoleName.SITE_MANAGER, false);
		when(users.findWithRoleById(11L)).thenReturn(Optional.of(inspector));
		when(users.findWithRoleById(12L)).thenReturn(Optional.of(inactive));

		assertThatThrownBy(() -> service.create(INCIDENT_ID, 5L, command(11L, TODAY)))
			.isInstanceOfSatisfying(InvalidFieldException.class, e -> assertThat(e.getField()).isEqualTo("assigneeId"));
		assertThatThrownBy(() -> service.create(INCIDENT_ID, 5L, command(12L, TODAY)))
			.isInstanceOf(InvalidFieldException.class);
	}

	@Test
	void create_onAClosedIncident_isRefused() {
		incident.setStatus(IncidentStatus.CLOSED);

		assertThatThrownBy(() -> service.create(INCIDENT_ID, 5L, command(null, TODAY)))
			.isInstanceOfSatisfying(InvalidTransitionException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(CorrectiveActionService.INCIDENT_CLOSED));
	}

	@Test
	void create_withAStaleIncidentVersion_isAConflict() {
		assertThatThrownBy(() -> service.create(INCIDENT_ID, 4L, command(null, TODAY)))
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

	@Test
	void create_onAnUnknownIncident_isNotFound() {
		when(incidents.findForChildUpdateById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(99L, 5L, command(null, TODAY))).isInstanceOf(NotFoundException.class);
	}

	@Test
	void update_movingTheDueDateOfAnOverdueActionToTheFuture_reopensIt() {
		CorrectiveAction action = action(1L, incident, CorrectiveActionStatus.OVERDUE, TODAY.minusDays(5), manager);

		service.update(INCIDENT_ID, 1L, 5L, command(7L, TODAY.plusDays(7)));

		assertThat(action.getStatus()).isEqualTo(CorrectiveActionStatus.OPEN);
		assertThat(action.getDueDate()).isEqualTo(TODAY.plusDays(7));
		assertThat(action.getAssignee()).as("unchanged assignee is kept without a lookup").isSameAs(manager);
	}

	@Test
	void update_keepingAPastDueDate_isAllowedAndStaysOverdue() {
		CorrectiveAction action = action(1L, incident, CorrectiveActionStatus.OVERDUE, TODAY.minusDays(5), null);

		service.update(INCIDENT_ID, 1L, 5L, command(null, TODAY.minusDays(5)));

		assertThat(action.getStatus()).isEqualTo(CorrectiveActionStatus.OVERDUE);
		assertThat(action.getDescription()).isEqualTo("Install toe boards");
	}

	@Test
	void update_changingToAPastDueDate_isAFieldError() {
		action(1L, incident, CorrectiveActionStatus.OPEN, TODAY.plusDays(5), null);

		assertThatThrownBy(() -> service.update(INCIDENT_ID, 1L, 5L, command(null, TODAY.minusDays(1))))
			.isInstanceOf(InvalidFieldException.class);
	}

	@Test
	void update_ofADoneAction_isRefused() {
		action(1L, incident, CorrectiveActionStatus.DONE, TODAY, null);

		assertThatThrownBy(() -> service.update(INCIDENT_ID, 1L, 5L, command(null, TODAY)))
			.isInstanceOfSatisfying(InvalidTransitionException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(CorrectiveActionService.ACTION_DONE));
	}

	@Test
	void update_ofAnActionOfAnotherIncident_isNotFound() {
		assertThatThrownBy(() -> service.update(INCIDENT_ID, 77L, 5L, command(null, TODAY)))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void markDone_setsDoneAndTheCompletionTime() {
		CorrectiveAction action = action(1L, incident, CorrectiveActionStatus.OVERDUE, TODAY.minusDays(1), manager);

		service.markDone(INCIDENT_ID, 1L, 5L);

		assertThat(action.getStatus()).isEqualTo(CorrectiveActionStatus.DONE);
		assertThat(action.getCompletedAt()).isEqualTo(NOW);
	}

	@Test
	void markDone_twice_isRefused() {
		action(1L, incident, CorrectiveActionStatus.DONE, TODAY, manager);

		assertThatThrownBy(() -> service.markDone(INCIDENT_ID, 1L, 5L)).isInstanceOf(InvalidTransitionException.class);
	}

	@Test
	void delete_removesAnOpenAction() {
		action(1L, incident, CorrectiveActionStatus.OPEN, TODAY, null);

		service.delete(INCIDENT_ID, 1L, 5L);

		assertThat(incident.getCorrectiveActions()).isEmpty();
	}

	@Test
	void get_returnsTheActionForTheEditForm() {
		action(1L, incident, CorrectiveActionStatus.OPEN, TODAY.plusDays(2), supervisor);

		CorrectiveActionView view = service.get(INCIDENT_ID, 1L);

		assertThat(view.assigneeId()).isEqualTo(9L);
		assertThat(view.overdue()).isFalse();
		assertThat(view.done()).isFalse();
	}

	@Test
	void get_ofAnUnknownIncident_isNotFound() {
		when(incidents.findDetailById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(99L, 1L)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void incidentForActions_returnsTheHeaderWithTheCurrentVersion() {
		IncidentHeader header = service.incidentForActions(INCIDENT_ID);

		assertThat(header.version()).isEqualTo(5L);
		assertThat(header.siteCode()).isEqualTo("ZG-01");
	}

	@Test
	void incidentForActions_ofAClosedIncident_isRefused() {
		incident.setStatus(IncidentStatus.CLOSED);

		assertThatThrownBy(() -> service.incidentForActions(INCIDENT_ID)).isInstanceOf(InvalidTransitionException.class);
	}

	@Test
	void assigneeOptions_areActiveManagersAndSupervisors() {
		when(users.findByRole_NameInAndActiveTrueOrderByFullNameAsc(CorrectiveActionService.ASSIGNEE_ROLES))
			.thenReturn(List.of(supervisor, manager));

		assertThat(service.assigneeOptions()).extracting(option -> option.fullName())
			.containsExactly("Ivana Babic", "Marko Kovacevic");
	}

}
