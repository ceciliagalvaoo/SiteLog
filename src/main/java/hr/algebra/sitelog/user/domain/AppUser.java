package hr.algebra.sitelog.user.domain;

import hr.algebra.sitelog.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Application user ({@code app_user}, because {@code user} is reserved in PostgreSQL).
 * Each user has exactly one role.
 */
@Entity
@Table(name = "app_user")
public class AppUser extends BaseEntity {

	/** Stored in lower case ({@code ck_app_user_email_lower}); used as the login name. */
	@Column(name = "email", nullable = false, unique = true)
	private String email;

	/** BCrypt hash, never the password itself. */
	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "full_name", nullable = false, length = 150)
	private String fullName;

	/** {@code en} or {@code hr} ({@code ck_app_user_language}); applied as locale after login. */
	@Column(name = "preferred_language", nullable = false, length = 2)
	private String preferredLanguage = "en";

	/** Inactive users cannot log in; they are kept because logs and incidents reference them. */
	@Column(name = "active", nullable = false)
	private boolean active = true;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "role_id", nullable = false)
	private Role role;

	protected AppUser() {
	}

	public AppUser(String email, String passwordHash, String fullName, Role role) {
		this.email = email.toLowerCase();
		this.passwordHash = passwordHash;
		this.fullName = fullName;
		this.role = role;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email.toLowerCase();
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public String getPreferredLanguage() {
		return preferredLanguage;
	}

	public void setPreferredLanguage(String preferredLanguage) {
		this.preferredLanguage = preferredLanguage;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Role getRole() {
		return role;
	}

	public void setRole(Role role) {
		this.role = role;
	}

}
