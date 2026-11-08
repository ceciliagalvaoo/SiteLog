package hr.algebra.sitelog.incident.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import hr.algebra.sitelog.common.domain.BaseEntity;
import hr.algebra.sitelog.crew.domain.Crew;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.user.domain.AppUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Safety incident, aggregate root of {@link CorrectiveAction} ({@code cascade = ALL},
 * {@code orphanRemoval = true}). Optimistic locking with {@link #version}, like DailyLog.
 *
 * <p>Relations: the daily log is <b>optional</b> (an incident may be reported without a log of
 * that day); affected crews are a plain {@code @ManyToMany} because the link has no attributes
 * (unlike {@code CrewAssignment}).
 */
@Entity
@Table(name = "incident")
public class Incident extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "site_id", nullable = false)
	private Site site;

	/** Optional relation: {@code daily_log_id} is nullable. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "daily_log_id")
	private DailyLog dailyLog;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "description", nullable = false)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "severity", nullable = false, length = 10)
	private Severity severity;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, length = 30)
	private IncidentCategory category;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private IncidentStatus status = IncidentStatus.REPORTED;

	// AR2: required for HIGH/CRITICAL before SIGNED_OFF (ck_incident_severity_fields).
	@Column(name = "root_cause")
	private String rootCause;

	@Column(name = "witnesses")
	private String witnesses;

	@Column(name = "medical_treatment")
	private String medicalTreatment;

	@Column(name = "authority_notified")
	private Boolean authorityNotified;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reported_by", nullable = false)
	private AppUser reportedBy;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "signed_off_by")
	private AppUser signedOffBy;

	@Column(name = "signed_off_at")
	private Instant signedOffAt;

	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "incident_affected_crew",
			joinColumns = @JoinColumn(name = "incident_id"),
			inverseJoinColumns = @JoinColumn(name = "crew_id"))
	@OrderBy("name ASC")
	private Set<Crew> affectedCrews = new LinkedHashSet<>();

	/** A Set for the same reason as {@code DailyLog.workEntries}: fetched together with affected crews. */
	@OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("dueDate ASC, id ASC")
	private Set<CorrectiveAction> correctiveActions = new LinkedHashSet<>();

	protected Incident() {
	}

	public Incident(Site site, Instant occurredAt, String title, String description, Severity severity,
			IncidentCategory category, AppUser reportedBy) {
		this.site = site;
		this.occurredAt = occurredAt;
		this.title = title;
		this.description = description;
		this.severity = severity;
		this.category = category;
		this.reportedBy = reportedBy;
	}

	public void addCorrectiveAction(CorrectiveAction action) {
		correctiveActions.add(action);
		action.setIncident(this);
	}

	public void removeCorrectiveAction(CorrectiveAction action) {
		correctiveActions.remove(action);
		action.setIncident(null);
	}

	public void addAffectedCrew(Crew crew) {
		affectedCrews.add(crew);
	}

	public void removeAffectedCrew(Crew crew) {
		affectedCrews.remove(crew);
	}

	public Site getSite() {
		return site;
	}

	public DailyLog getDailyLog() {
		return dailyLog;
	}

	public void setDailyLog(DailyLog dailyLog) {
		this.dailyLog = dailyLog;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public void setOccurredAt(Instant occurredAt) {
		this.occurredAt = occurredAt;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Severity getSeverity() {
		return severity;
	}

	public void setSeverity(Severity severity) {
		this.severity = severity;
	}

	public IncidentCategory getCategory() {
		return category;
	}

	public void setCategory(IncidentCategory category) {
		this.category = category;
	}

	public IncidentStatus getStatus() {
		return status;
	}

	public void setStatus(IncidentStatus status) {
		this.status = status;
	}

	public String getRootCause() {
		return rootCause;
	}

	public void setRootCause(String rootCause) {
		this.rootCause = rootCause;
	}

	public String getWitnesses() {
		return witnesses;
	}

	public void setWitnesses(String witnesses) {
		this.witnesses = witnesses;
	}

	public String getMedicalTreatment() {
		return medicalTreatment;
	}

	public void setMedicalTreatment(String medicalTreatment) {
		this.medicalTreatment = medicalTreatment;
	}

	public Boolean getAuthorityNotified() {
		return authorityNotified;
	}

	public void setAuthorityNotified(Boolean authorityNotified) {
		this.authorityNotified = authorityNotified;
	}

	public AppUser getReportedBy() {
		return reportedBy;
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

	public Set<Crew> getAffectedCrews() {
		return Collections.unmodifiableSet(affectedCrews);
	}

	public Set<CorrectiveAction> getCorrectiveActions() {
		return Collections.unmodifiableSet(correctiveActions);
	}

}
