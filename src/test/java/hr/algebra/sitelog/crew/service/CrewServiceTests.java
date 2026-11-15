package hr.algebra.sitelog.crew.service;

import static hr.algebra.sitelog.support.TestEntities.crew;
import static hr.algebra.sitelog.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.crew.domain.Crew;
import hr.algebra.sitelog.crew.repository.CrewRepository;
import hr.algebra.sitelog.dailylog.repository.CrewAssignmentRepository;
import hr.algebra.sitelog.incident.repository.IncidentRepository;

@ExtendWith(MockitoExtension.class)
class CrewServiceTests {

	@Mock
	CrewRepository crews;

	@Mock
	CrewAssignmentRepository assignments;

	@Mock
	IncidentRepository incidents;

	@InjectMocks
	CrewService service;

	@Test
	void command_trimsTextFields() {
		CrewCommand command = new CrewCommand("  Formwork A ", " Gradnja ", " Carpentry ", true);

		assertThat(command.name()).isEqualTo("Formwork A");
		assertThat(command.company()).isEqualTo("Gradnja");
		assertThat(command.trade()).isEqualTo("Carpentry");
	}

	@Test
	void list_returnsActiveAndInactiveCrewsInRepositoryOrder() {
		when(crews.findAllByOrderByNameAsc()).thenReturn(List.of(crew(1L, "Alpha", true), crew(2L, "Bravo", false)));

		assertThat(service.list()).extracting(CrewView::name, CrewView::active)
			.containsExactly(tuple("Alpha", true),
					tuple("Bravo", false));
	}

	@Test
	void get_whenCrewMissing_throwsNotFoundException() {
		when(crews.findById(9L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(9L)).isInstanceOf(NotFoundException.class).hasMessageContaining("Crew 9");
	}

	@Test
	void get_mapsCrewToView() {
		when(crews.findById(1L)).thenReturn(Optional.of(crew(1L, "Alpha", true)));

		CrewView view = service.get(1L);

		assertThat(view.id()).isEqualTo(1L);
		assertThat(view.company()).isEqualTo("Gradnja d.o.o.");
		assertThat(view.trade()).isEqualTo("Formwork");
	}

	@Test
	void activeOptions_listsOnlyActiveCrews() {
		when(crews.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(crew(1L, "Alpha", true)));

		assertThat(service.activeOptions()).extracting(CrewOption::id).containsExactly(1L);
	}

	@Test
	void optionsIncluding_whenSelectedCrewsAreActive_doesNotLoadAnyOtherCrew() {
		when(crews.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(crew(1L, "Alpha", true), crew(2L, "Bravo", true)));

		List<CrewOption> options = service.optionsIncluding(Arrays.asList(1L, null, 2L));

		assertThat(options).extracting(CrewOption::id).containsExactly(1L, 2L);
		verify(crews, never()).findAllById(any());
	}

	@Test
	void optionsIncluding_withDeactivatedSelectedCrew_addsItOnceSortedByName() {
		when(crews.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(crew(1L, "alpha", true), crew(2L, "Delta", true)));
		when(crews.findAllById(List.of(3L))).thenReturn(List.of(crew(3L, "Charlie", false)));

		List<CrewOption> options = service.optionsIncluding(List.of(3L, 1L, 3L));

		assertThat(options).extracting(CrewOption::name).containsExactly("alpha", "Charlie", "Delta");
		assertThat(options).filteredOn(o -> o.id().equals(3L)).extracting(CrewOption::active).containsExactly(false);
	}

	@Test
	void create_withNewName_savesCrewWithActiveFlag() {
		when(crews.existsByNameIgnoreCase("Formwork A")).thenReturn(false);
		when(crews.save(any(Crew.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 5L));

		Long id = service.create(new CrewCommand("Formwork A", "Gradnja", "Carpentry", false));

		assertThat(id).isEqualTo(5L);
		ArgumentCaptor<Crew> saved = ArgumentCaptor.forClass(Crew.class);
		verify(crews).save(saved.capture());
		assertThat(saved.getValue().getName()).isEqualTo("Formwork A");
		assertThat(saved.getValue().getCompany()).isEqualTo("Gradnja");
		assertThat(saved.getValue().isActive()).isFalse();
	}

	@Test
	void create_whenNameAlreadyExists_throwsInvalidFieldOnName() {
		when(crews.existsByNameIgnoreCase("Formwork A")).thenReturn(true);

		assertThatThrownBy(() -> service.create(new CrewCommand("Formwork A", "Gradnja", "Carpentry", true)))
			.isInstanceOfSatisfying(InvalidFieldException.class, e -> {
				assertThat(e.getField()).isEqualTo("name");
				assertThat(e.getMessageKey()).isEqualTo(CrewService.NAME_DUPLICATE);
			});
		verify(crews, never()).save(any());
	}

	@Test
	void update_withValidCommand_changesTheManagedCrew() {
		Crew crew = crew(1L, "Alpha", true);
		when(crews.findById(1L)).thenReturn(Optional.of(crew));
		when(crews.existsByNameIgnoreCaseAndIdNot("Alpha 2", 1L)).thenReturn(false);

		service.update(1L, new CrewCommand("Alpha 2", "New Co", "Rebar", false));

		assertThat(crew.getName()).isEqualTo("Alpha 2");
		assertThat(crew.getCompany()).isEqualTo("New Co");
		assertThat(crew.getTrade()).isEqualTo("Rebar");
		assertThat(crew.isActive()).isFalse();
	}

	@Test
	void update_whenNameBelongsToAnotherCrew_throwsInvalidFieldAndKeepsOldName() {
		Crew crew = crew(1L, "Alpha", true);
		when(crews.findById(1L)).thenReturn(Optional.of(crew));
		when(crews.existsByNameIgnoreCaseAndIdNot("Bravo", 1L)).thenReturn(true);

		assertThatThrownBy(() -> service.update(1L, new CrewCommand("Bravo", "Co", "Trade", true)))
			.isInstanceOfSatisfying(InvalidFieldException.class, e -> assertThat(e.getField()).isEqualTo("name"));
		assertThat(crew.getName()).isEqualTo("Alpha");
	}

	@Test
	void delete_whenCrewIsUsedInCrewAssignment_throwsDeletionNotAllowed() {
		when(crews.findById(1L)).thenReturn(Optional.of(crew(1L, "Alpha", true)));
		when(assignments.existsByCrew_Id(1L)).thenReturn(true);

		assertDeleteBlocked();
	}

	@Test
	void delete_whenCrewIsAffectedByIncident_throwsDeletionNotAllowed() {
		when(crews.findById(1L)).thenReturn(Optional.of(crew(1L, "Alpha", true)));
		when(assignments.existsByCrew_Id(1L)).thenReturn(false);
		when(incidents.existsByAffectedCrews_Id(1L)).thenReturn(true);

		assertDeleteBlocked();
	}

	private void assertDeleteBlocked() {
		assertThatThrownBy(() -> service.delete(1L))
			.isInstanceOfSatisfying(DeletionNotAllowedException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(CrewService.DELETE_BLOCKED));
		verify(crews, never()).delete(any());
	}

	@Test
	void delete_whenCrewIsUnused_deletesIt() {
		Crew crew = crew(1L, "Alpha", true);
		when(crews.findById(1L)).thenReturn(Optional.of(crew));

		service.delete(1L);

		verify(crews).delete(crew);
	}

}
