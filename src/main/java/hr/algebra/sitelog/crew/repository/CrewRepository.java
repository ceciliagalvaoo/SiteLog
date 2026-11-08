package hr.algebra.sitelog.crew.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import hr.algebra.sitelog.crew.domain.Crew;

public interface CrewRepository extends JpaRepository<Crew, Long> {

	/** Crews offered in selection lists (crew assignment rows, affected crews). */
	List<Crew> findByActiveTrueOrderByNameAsc();

	/** Crew management list, active and inactive. */
	List<Crew> findAllByOrderByNameAsc();

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

}
