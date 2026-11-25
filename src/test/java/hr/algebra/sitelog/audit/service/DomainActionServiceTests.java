package hr.algebra.sitelog.audit.service;

import static hr.algebra.sitelog.support.TestEntities.NOW;
import static hr.algebra.sitelog.support.TestEntities.manager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import hr.algebra.sitelog.audit.domain.ActionOutcome;
import hr.algebra.sitelog.audit.domain.AuditedEntityType;
import hr.algebra.sitelog.audit.domain.DomainAction;
import hr.algebra.sitelog.audit.domain.DomainActionType;
import hr.algebra.sitelog.audit.repository.DomainActionRepository;
import hr.algebra.sitelog.support.TestEntities;
import hr.algebra.sitelog.user.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class DomainActionServiceTests {

	@Mock
	DomainActionRepository actions;

	@Mock
	AppUserRepository users;

	@Test
	void timeline_resolvesActorNamesInOneQueryAndKeepsTheEmailOfUnknownActors() {
		DomainAction byManager = new DomainAction(DomainActionType.INCIDENT_REPORTED, 30L, 7L, "manager@sitelog.test",
				NOW, ActionOutcome.SUCCESS, 5, null);
		DomainAction byDeleted = new DomainAction(DomainActionType.INCIDENT_UPDATED, 30L, 8L, "gone@sitelog.test", NOW,
				ActionOutcome.SUCCESS, 3, null);
		DomainAction bySystem = new DomainAction(DomainActionType.INCIDENT_CLOSED, 30L, null, null, NOW,
				ActionOutcome.SUCCESS, 1, null);
		when(actions.findByEntityTypeAndEntityIdAndOutcomeOrderByOccurredAtAscIdAsc(AuditedEntityType.INCIDENT, 30L,
				ActionOutcome.SUCCESS)).thenReturn(List.of(byManager, byDeleted, bySystem));
		when(users.findAllById(List.of(7L, 8L))).thenReturn(List.of(manager(7L)));

		List<DomainActionView> timeline = new DomainActionService(actions, users, TestEntities.fixedClock())
			.timeline(AuditedEntityType.INCIDENT, 30L);

		assertThat(timeline).extracting(DomainActionView::actorName)
			.containsExactly("Marko Kovacevic", "gone@sitelog.test", null);
		assertThat(timeline.get(0).messageKey()).isEqualTo("audit.action.INCIDENT_REPORTED");
		assertThat(timeline.get(0).occurredAt().getZone()).isEqualTo(TestEntities.ZONE);
	}

	@Test
	void timeline_withoutActors_doesNotQueryUsers() {
		when(actions.findByEntityTypeAndEntityIdAndOutcomeOrderByOccurredAtAscIdAsc(any(), any(), any()))
			.thenReturn(List.of());

		assertThat(new DomainActionService(actions, users, TestEntities.fixedClock())
			.timeline(AuditedEntityType.DAILY_LOG, 1L)).isEmpty();
		verify(users, never()).findAllById(any());
	}

	@Test
	void recorder_savesOneRowWithTheEntityTypeOfTheAction() {
		new DomainActionRecorder(actions).record(DomainActionType.LOG_SIGNED_OFF, 10L, 7L, "m@x", NOW,
				ActionOutcome.DENIED, -3, "x".repeat(600));

		ArgumentCaptor<DomainAction> saved = ArgumentCaptor.forClass(DomainAction.class);
		verify(actions).save(saved.capture());
		assertThat(saved.getValue().getEntityType()).isEqualTo(AuditedEntityType.DAILY_LOG);
		assertThat(saved.getValue().getEntityId()).isEqualTo(10L);
		assertThat(saved.getValue().getOutcome()).isEqualTo(ActionOutcome.DENIED);
		assertThat(saved.getValue().getDurationMs()).isZero();
		assertThat(saved.getValue().getDetail()).hasSize(500);
		assertThat(saved.getValue().getActorEmail()).isEqualTo("m@x");
		assertThat(saved.getValue().getActorId()).isEqualTo(7L);
		assertThat(saved.getValue().getOccurredAt()).isEqualTo(NOW);
		assertThat(saved.getValue().getAction()).isEqualTo(DomainActionType.LOG_SIGNED_OFF);
	}

}
