package hr.algebra.sitelog.crew.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.crew.domain.Crew;
import hr.algebra.sitelog.crew.repository.CrewRepository;
import hr.algebra.sitelog.dailylog.repository.CrewAssignmentRepository;
import hr.algebra.sitelog.incident.repository.IncidentRepository;

/**
 * Crew management (admin reference data).
 *
 * <p>Delete rule (decided 07/11): a crew can only be deleted while nothing references it. A crew
 * used in a crew assignment or listed as affected by an incident is part of a record and is
 * deactivated instead ({@code active = false}): it disappears from the selection lists but the
 * history keeps pointing to it.
 */
@Service
@Transactional(readOnly = true)
public class CrewService {

	public static final String NAME_DUPLICATE = "validation.crew.name.duplicate";

	public static final String DELETE_BLOCKED = "crew.flash.deleteBlocked";

	private final CrewRepository crews;

	private final CrewAssignmentRepository assignments;

	private final IncidentRepository incidents;

	public CrewService(CrewRepository crews, CrewAssignmentRepository assignments, IncidentRepository incidents) {
		this.crews = crews;
		this.assignments = assignments;
		this.incidents = incidents;
	}

	/** Crew management list: active and inactive, by name. */
	public List<CrewView> list() {
		return crews.findAllByOrderByNameAsc().stream().map(CrewView::of).toList();
	}

	public CrewView get(Long id) {
		return CrewView.of(load(id));
	}

	/** Options for a crew select box: active crews only. */
	public List<CrewOption> activeOptions() {
		return crews.findByActiveTrueOrderByNameAsc().stream().map(CrewOption::of).toList();
	}

	/**
	 * Active crews plus the given ones even if inactive: editing an older log must still show the
	 * (now deactivated) crew already assigned on a row, but it is not offered for new rows.
	 */
	public List<CrewOption> optionsIncluding(Collection<Long> selectedIds) {
		List<Crew> active = crews.findByActiveTrueOrderByNameAsc();
		List<Long> missing = selectedIds.stream()
			.filter(id -> id != null && active.stream().noneMatch(c -> c.getId().equals(id)))
			.distinct()
			.toList();
		if (missing.isEmpty()) {
			return active.stream().map(CrewOption::of).toList();
		}
		return Stream.concat(active.stream(), crews.findAllById(missing).stream())
			.sorted(Comparator.comparing(Crew::getName, String.CASE_INSENSITIVE_ORDER))
			.map(CrewOption::of)
			.toList();
	}

	@Transactional
	public Long create(CrewCommand command) {
		if (crews.existsByNameIgnoreCase(command.name())) {
			throw new InvalidFieldException("name", NAME_DUPLICATE);
		}
		Crew crew = new Crew(command.name(), command.company(), command.trade());
		crew.setActive(command.active());
		return crews.save(crew).getId();
	}

	@Transactional
	public void update(Long id, CrewCommand command) {
		Crew crew = load(id);
		if (crews.existsByNameIgnoreCaseAndIdNot(command.name(), id)) {
			throw new InvalidFieldException("name", NAME_DUPLICATE);
		}
		crew.setName(command.name());
		crew.setCompany(command.company());
		crew.setTrade(command.trade());
		crew.setActive(command.active());
	}

	/** Deletes an unused crew; a crew in use must be deactivated instead. */
	@Transactional
	public void delete(Long id) {
		Crew crew = load(id);
		if (assignments.existsByCrew_Id(id) || incidents.existsByAffectedCrews_Id(id)) {
			throw new DeletionNotAllowedException(DELETE_BLOCKED, "crew " + id + " is in use");
		}
		crews.delete(crew);
	}

	private Crew load(Long id) {
		return crews.findById(id).orElseThrow(() -> new NotFoundException(Crew.class, id));
	}

}
