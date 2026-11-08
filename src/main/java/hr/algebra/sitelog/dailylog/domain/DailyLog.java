package hr.algebra.sitelog.dailylog.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import hr.algebra.sitelog.common.domain.BaseEntity;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.user.domain.AppUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Aggregate root of {@code DailyLog -> WorkEntry -> CrewAssignment} (ADR-0001 item 3): the log
 * owns its work entries ({@code cascade = ALL}, {@code orphanRemoval = true}), each work entry
 * owns its crew assignments.
 *
 * <p>{@link #version} gives optimistic locking (409 on a stale write). Changes to children do
 * not bump it by themselves, so services that change a child load the log with
 * {@code LockModeType.OPTIMISTIC_FORCE_INCREMENT} ({@code DailyLogRepository#findForChildUpdateById}).
 *
 * <p>Audited by Envers (ADR-0003). Associations to reference data are {@code NOT_AUDITED}: the
 * audit row keeps the foreign key, the site/user itself is not audited.
 */
@Entity
@Table(name = "daily_log")
@Audited
public class DailyLog extends BaseEntity {

	@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "site_id", nullable = false)
	private Site site;

	/** One log per site and day ({@code uk_daily_log_site_date}). */
	@Column(name = "log_date", nullable = false)
	private LocalDate logDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private LogStatus status = LogStatus.DRAFT;

	/** Number of approved reopens; the lock trigger only lets SIGNED_OFF -> REOPENED through with +1. */
	@Column(name = "revision", nullable = false)
	private int revision;

	@Enumerated(EnumType.STRING)
	@Column(name = "weather", length = 20)
	private Weather weather;

	@Column(name = "notes")
	private String notes;

	@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "signed_off_by")
	private AppUser signedOffBy;

	@Column(name = "signed_off_at")
	private Instant signedOffAt;

	/** Not audited by Envers (default {@code do_not_audit_optimistic_locking_field=true}). */
	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	/**
	 * A {@code Set}, not a {@code List}: the detail query join-fetches work entries and their
	 * assignments together; with a List (a "bag") each work entry would appear once per
	 * assignment row of the SQL result. {@code @OrderBy} makes Hibernate use an ordered set.
	 */
	@OneToMany(mappedBy = "dailyLog", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("startTime ASC, id ASC")
	private Set<WorkEntry> workEntries = new LinkedHashSet<>();

	protected DailyLog() {
	}

	public DailyLog(Site site, LocalDate logDate) {
		this.site = site;
		this.logDate = logDate;
	}

	/** Adds a work entry and sets its back-reference (keeps both sides of the relation in sync). */
	public void addWorkEntry(WorkEntry workEntry) {
		workEntries.add(workEntry);
		workEntry.setDailyLog(this);
	}

	/** Removes a work entry; {@code orphanRemoval} deletes it and its assignments on flush. */
	public void removeWorkEntry(WorkEntry workEntry) {
		workEntries.remove(workEntry);
		workEntry.setDailyLog(null);
	}

	public Site getSite() {
		return site;
	}

	public LocalDate getLogDate() {
		return logDate;
	}

	public void setLogDate(LocalDate logDate) {
		this.logDate = logDate;
	}

	public LogStatus getStatus() {
		return status;
	}

	public void setStatus(LogStatus status) {
		this.status = status;
	}

	public int getRevision() {
		return revision;
	}

	public void setRevision(int revision) {
		this.revision = revision;
	}

	public Weather getWeather() {
		return weather;
	}

	public void setWeather(Weather weather) {
		this.weather = weather;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public AppUser getSignedOffBy() {
		return signedOffBy;
	}

	public void setSignedOffBy(AppUser signedOffBy) {
		this.signedOffBy = signedOffBy;
	}

	public Instant getSignedOffAt() {
		return signedOffAt;
	}

	public void setSignedOffAt(Instant signedOffAt) {
		this.signedOffAt = signedOffAt;
	}

	public Long getVersion() {
		return version;
	}

	/** Read-only view; use {@link #addWorkEntry} / {@link #removeWorkEntry} to change it. */
	public Set<WorkEntry> getWorkEntries() {
		return Collections.unmodifiableSet(workEntries);
	}

}
