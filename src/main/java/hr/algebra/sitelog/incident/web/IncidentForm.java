package hr.algebra.sitelog.incident.web;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import hr.algebra.sitelog.crew.service.CrewOption;
import hr.algebra.sitelog.incident.domain.IncidentCategory;
import hr.algebra.sitelog.incident.domain.Severity;
import hr.algebra.sitelog.incident.service.IncidentCommand;
import hr.algebra.sitelog.incident.service.IncidentDetails;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Report / edit incident form. Field names = {@code IncidentCommand} names, so a service
 * {@code InvalidFieldException("dailyLogId", ...)} lands on the right field.
 *
 * <p>{@code occurredAt} comes from an {@code <input type="datetime-local">} in the sites' time
 * zone and becomes an {@code Instant} in {@link #toCommand}. The four AR2 fields carry the
 * {@link HighSeverity} validation group; the controller validates that group only when
 * {@link #requiresExtendedFields()} (HIGH / CRITICAL), in the same {@code BindingResult}, so the
 * user sees every missing field at once.
 */
public class IncidentForm {

	@NotNull(message = "{validation.incident.occurredAt.required}")
	@DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
	private LocalDateTime occurredAt;

	@NotBlank(message = "{validation.incident.title.required}")
	@Size(max = 200, message = "{validation.size.max}")
	private String title;

	@NotBlank(message = "{validation.incident.description.required}")
	@Size(max = 5000, message = "{validation.size.max}")
	private String description;

	@NotNull(message = "{validation.incident.severity.required}")
	private Severity severity;

	@NotNull(message = "{validation.incident.category.required}")
	private IncidentCategory category;

	/** Optional link to a daily log of the same site. */
	private Long dailyLogId;

	private List<Long> affectedCrewIds = new ArrayList<>();

	@NotBlank(groups = HighSeverity.class, message = "{validation.incident.rootCause.required}")
	@Size(max = 5000, message = "{validation.size.max}")
	private String rootCause;

	@NotBlank(groups = HighSeverity.class, message = "{validation.incident.witnesses.required}")
	@Size(max = 2000, message = "{validation.size.max}")
	private String witnesses;

	@NotBlank(groups = HighSeverity.class, message = "{validation.incident.medicalTreatment.required}")
	@Size(max = 2000, message = "{validation.size.max}")
	private String medicalTreatment;

	@NotNull(groups = HighSeverity.class, message = "{validation.incident.authorityNotified.required}")
	private Boolean authorityNotified;

	/** Version shown when the edit form was loaded (optimistic locking); null on create. */
	private Long version;

	/** AR2: HIGH and CRITICAL need root cause, witnesses, medical treatment and authority notified. */
	public boolean requiresExtendedFields() {
		return severity != null && severity.requiresExtendedFields();
	}

	/** Empty form, "occurred" defaulting to now (minutes) in the sites' zone. */
	public static IncidentForm blank(Clock clock) {
		IncidentForm form = new IncidentForm();
		form.occurredAt = LocalDateTime.now(clock).withSecond(0).withNano(0);
		return form;
	}

	public static IncidentForm from(IncidentDetails incident) {
		IncidentForm form = new IncidentForm();
		form.occurredAt = incident.occurredAt().toLocalDateTime().withSecond(0).withNano(0);
		form.title = incident.title();
		form.description = incident.description();
		form.severity = incident.severity();
		form.category = incident.category();
		form.dailyLogId = incident.dailyLogId();
		form.affectedCrewIds = new ArrayList<>(incident.affectedCrews().stream().map(CrewOption::id).toList());
		form.rootCause = incident.rootCause();
		form.witnesses = incident.witnesses();
		form.medicalTreatment = incident.medicalTreatment();
		form.authorityNotified = incident.authorityNotified();
		form.version = incident.version();
		return form;
	}

	public IncidentCommand toCommand(ZoneId zone) {
		return new IncidentCommand(occurredAt == null ? null : occurredAt.atZone(zone).toInstant(), trim(title),
				trim(description), severity, category, dailyLogId,
				affectedCrewIds == null ? List.of() : affectedCrewIds.stream().filter(java.util.Objects::nonNull).toList(),
				rootCause, witnesses, medicalTreatment, authorityNotified);
	}

	private static String trim(String value) {
		return value == null ? null : value.strip();
	}

	public LocalDateTime getOccurredAt() {
		return occurredAt;
	}

	public void setOccurredAt(LocalDateTime occurredAt) {
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

	public Long getDailyLogId() {
		return dailyLogId;
	}

	public void setDailyLogId(Long dailyLogId) {
		this.dailyLogId = dailyLogId;
	}

	public List<Long> getAffectedCrewIds() {
		return affectedCrewIds;
	}

	public void setAffectedCrewIds(List<Long> affectedCrewIds) {
		this.affectedCrewIds = affectedCrewIds;
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

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

}
