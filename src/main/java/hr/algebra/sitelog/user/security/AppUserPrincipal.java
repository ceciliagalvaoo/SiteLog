package hr.algebra.sitelog.user.security;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import hr.algebra.sitelog.user.domain.AppUser;
import hr.algebra.sitelog.user.domain.RoleName;

/**
 * The signed-in user as Spring Security keeps it in the HTTP session. A copy of the
 * {@code app_user} row taken at login (never the JPA entity: the session outlives the
 * persistence context).
 *
 * <p>Authority: {@code ROLE_<role>} (one role per user), so {@code hasRole('ADMIN')} works in
 * {@code @PreAuthorize} and in {@code sec:authorize}. {@link #isEnabled()} is the {@code active}
 * flag: the authentication provider refuses a disabled user before the password is checked.
 */
public final class AppUserPrincipal implements UserDetails, CredentialsContainer {

	private static final long serialVersionUID = 1L;

	private final Long id;

	private final String email;

	private String passwordHash;

	private final String fullName;

	private final RoleName role;

	private final String preferredLanguage;

	private final boolean active;

	public AppUserPrincipal(Long id, String email, String passwordHash, String fullName, RoleName role,
			String preferredLanguage, boolean active) {
		this.id = id;
		this.email = email;
		this.passwordHash = passwordHash;
		this.fullName = fullName;
		this.role = role;
		this.preferredLanguage = preferredLanguage;
		this.active = active;
	}

	public static AppUserPrincipal of(AppUser user) {
		return new AppUserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), user.getFullName(),
				user.getRole().getName(), user.getPreferredLanguage(), user.isActive());
	}

	public static String authority(RoleName role) {
		return "ROLE_" + role.name();
	}

	public Long getId() {
		return id;
	}

	public String getFullName() {
		return fullName;
	}

	public RoleName getRole() {
		return role;
	}

	public String getPreferredLanguage() {
		return preferredLanguage;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority(authority(role)));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	/** The login name is the e-mail address. */
	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isEnabled() {
		return active;
	}

	/** Called by the authentication manager after a successful login: the hash is not kept in the session. */
	@Override
	public void eraseCredentials() {
		passwordHash = null;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof AppUserPrincipal principal && Objects.equals(id, principal.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public String toString() {
		return "AppUserPrincipal[id=" + id + ", email=" + email + ", role=" + role + "]";
	}

}
