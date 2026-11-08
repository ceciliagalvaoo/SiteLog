package hr.algebra.sitelog.dailylog.domain;

import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.envers.Audited;

import hr.algebra.sitelog.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * One activity on a daily log (what was done, where, from/to). Child of {@link DailyLog};
 * owns its {@link CrewAssignment}s.
 *
 * <p>The assignments are a {@code Set}: a crew appears at most once per work entry
 * ({@code uk_crew_assignment_entry_crew}), and two {@code List}s ("bags") could not be
 * join-fetched in one query (Hibernate's {@code MultipleBagFetchException}).
 */
@Entity
@Table(name = "work_entry")
@Audited
public class WorkEntry extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "daily_log_id", nullable = false)
	private DailyLog dailyLog;

	@Column(name = "description", nullable = false, length = 500)
	private String description;

	@Column(name = "location", length = 150)
	private String location;

	@Column(name = "start_time")
	private LocalTime startTime;

	/** After {@link #startTime} when both are set ({@code ck_work_entry_times}). */
	@Column(name = "end_time")
	private LocalTime endTime;

	@OneToMany(mappedBy = "workEntry", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id ASC")
	private Set<CrewAssignment> assignments = new LinkedHashSet<>();

	protected WorkEntry() {
	}

	public WorkEntry(String description) {
		this.description = description;
	}

	public void addAssignment(CrewAssignment assignment) {
		assignments.add(assignment);
		assignment.setWorkEntry(this);
	}

	public void removeAssignment(CrewAssignment assignment) {
		assignments.remove(assignment);
		assignment.setWorkEntry(null);
	}

	public DailyLog getDailyLog() {
		return dailyLog;
	}

	void setDailyLog(DailyLog dailyLog) {
		this.dailyLog = dailyLog;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public void setStartTime(LocalTime startTime) {
		this.startTime = startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public void setEndTime(LocalTime endTime) {
		this.endTime = endTime;
	}

	/** Read-only view; use {@link #addAssignment} / {@link #removeAssignment} to change it. */
	public Set<CrewAssignment> getAssignments() {
		return Collections.unmodifiableSet(assignments);
	}

}
