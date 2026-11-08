package hr.algebra.sitelog.crew.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import hr.algebra.sitelog.crew.domain.Crew;
import hr.algebra.sitelog.support.JpaFixtures;
import hr.algebra.sitelog.support.RepositoryTest;
import jakarta.persistence.EntityManager;

/** {@link CrewRepository}: five active crews and one inactive ("Perić Plumbing") in the seed. */
@RepositoryTest
class CrewRepositoryTests {

	private final CrewRepository crews;
	private final JpaFixtures fixtures;

	@Autowired
	CrewRepositoryTests(CrewRepository crews, EntityManager em) {
		this.crews = crews;
		this.fixtures = new JpaFixtures(em);
	}

	@Test
	void findByActiveTrueOrderByNameAsc_excludesInactiveCrewsAndSortsByName() {
		Crew inactive = fixtures.newCrew(false);

		assertThat(crews.findByActiveTrueOrderByNameAsc()).extracting(Crew::getName)
			.containsExactly("Babić Concrete", "Dalmacija Scaffolding", "Elektro Novak", "Jurić Steel Fixers",
					"Kovač Formwork")
			.doesNotContain("Perić Plumbing", inactive.getName());
	}

	@Test
	void findAllByOrderByNameAsc_includesInactiveCrews() {
		assertThat(crews.findAllByOrderByNameAsc()).extracting(Crew::getName)
			.hasSize(6)
			.contains("Perić Plumbing")
			.isSorted();
	}

	@Test
	void existsByNameIgnoreCase_andExcludingOwnId_supportCreateAndRename() {
		Crew elektro = fixtures.crew("Elektro Novak");

		assertThat(crews.existsByNameIgnoreCase("elektro NOVAK")).isTrue();
		assertThat(crews.existsByNameIgnoreCase("Elektro Horvat")).isFalse();
		// renaming a crew to its own name is not a conflict, renaming another crew to it is
		assertThat(crews.existsByNameIgnoreCaseAndIdNot("ELEKTRO novak", elektro.getId())).isFalse();
		assertThat(crews.existsByNameIgnoreCaseAndIdNot("ELEKTRO novak", fixtures.crew("Babić Concrete").getId()))
			.isTrue();
	}

	@Test
	void save_whenNameAlreadyExists_isRejectedByUniqueConstraint() {
		assertThatThrownBy(() -> crews.saveAndFlush(new Crew("Elektro Novak", "Other d.o.o.", "Electrical")))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uk_crew_name");
	}

}
