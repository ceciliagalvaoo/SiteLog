package hr.algebra.sitelog.common.audit;

import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Envers revision entity (ADR-0003): one row in {@code revinfo} per transaction that changed
 * audited data (DailyLog, WorkEntry, CrewAssignment). Besides the default number and timestamp
 * it stores who made the change.
 *
 * <p>{@code revinfo} is append-only (V5 triggers reject UPDATE/DELETE), so the username is
 * filled once, in {@link AuditRevisionListener#newRevision}, and never updated afterwards.
 */
@Entity
@Table(name = "revinfo")
@RevisionEntity(AuditRevisionListener.class)
public class AuditRevision {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@RevisionNumber
	@Column(name = "rev")
	private Long rev;

	/** Epoch milliseconds, set by Envers. */
	@RevisionTimestamp
	@Column(name = "revtstmp", nullable = false)
	private long timestamp;

	/** E-mail of the authenticated user, or {@link AuditRevisionListener#SYSTEM_USER}. */
	@Column(name = "username")
	private String username;

	public Long getRev() {
		return rev;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public String getUsername() {
		return username;
	}

	void setUsername(String username) {
		this.username = username;
	}

}
