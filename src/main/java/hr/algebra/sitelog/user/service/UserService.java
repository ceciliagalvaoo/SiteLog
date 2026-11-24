package hr.algebra.sitelog.user.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.site.repository.SiteRepository;
import hr.algebra.sitelog.user.domain.AppUser;
import hr.algebra.sitelog.user.domain.Role;
import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.repository.AppUserRepository;
import hr.algebra.sitelog.user.repository.RoleRepository;

/**
 * User administration (Admin) and the user's own profile.
 *
 * <p>Admin ({@code hasRole('ADMIN')} on the class):
 * <ul>
 * <li>create a user with an initial password (BCrypt hash, never stored in clear);</li>
 * <li>edit e-mail, name, role, language; activate / deactivate. Users are never deleted: logs,
 * incidents and audit rows point to them. An inactive user cannot sign in.</li>
 * <li>Admins cannot lock themselves out: no deactivating and no role change of their own
 * account.</li>
 * <li>assign sites to a site manager ({@code site.manager_id}). A site always has exactly one
 * manager, so a site is taken away from a manager only by assigning it to another one; for the
 * same reason a user who still manages sites keeps the SITE_MANAGER role.</li>
 * </ul>
 * Every signed-in user ({@code isAuthenticated()} on the method): own profile, preferred
 * language and password change (current password required, confirmation, length policy).
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMIN')")
public class UserService {

	public static final List<String> LANGUAGES = List.of("en", "hr");

	public static final String EMAIL_DUPLICATE = "validation.user.email.duplicate";

	public static final String LANGUAGE_INVALID = "validation.user.language.invalid";

	public static final String SELF_DEACTIVATE = "validation.user.self.deactivate";

	public static final String SELF_ROLE = "validation.user.self.role";

	public static final String ROLE_MANAGES_SITES = "validation.user.role.managesSites";

	public static final String SITES_NOT_MANAGER = "validation.user.sites.notManager";

	public static final String SITES_UNASSIGN = "validation.user.sites.unassign";

	public static final String SELF_DEACTIVATE_FLASH = "user.flash.selfDeactivate";

	public static final String CURRENT_PASSWORD_WRONG = "validation.password.current.wrong";

	public static final String CONFIRM_MISMATCH = "validation.password.confirm.mismatch";

	public static final String SAME_AS_CURRENT = "validation.password.sameAsCurrent";

	private final AppUserRepository users;

	private final RoleRepository roles;

	private final SiteRepository sites;

	private final PasswordEncoder passwordEncoder;

	private final CurrentUserProvider currentUser;

	public UserService(AppUserRepository users, RoleRepository roles, SiteRepository sites,
			PasswordEncoder passwordEncoder, CurrentUserProvider currentUser) {
		this.users = users;
		this.roles = roles;
		this.sites = sites;
		this.passwordEncoder = passwordEncoder;
		this.currentUser = currentUser;
	}

	// ------------------------------------------------------------------ Admin

	/** Users list with an optional filter on name or e-mail; the role comes in the same query. */
	public Page<UserListItem> list(String filter, Pageable pageable) {
		String text = filter == null ? "" : filter.trim();
		return users.searchWithRole(text, pageable).map(UserListItem::of);
	}

	public UserView get(Long id) {
		return toView(loadWithRole(id));
	}

	/** Creates an active user; returns the new id. */
	@Transactional
	public Long create(UserCommand command) {
		String email = normalizeEmail(command.email());
		if (users.existsByEmailIgnoreCase(email)) {
			throw new InvalidFieldException("email", EMAIL_DUPLICATE);
		}
		checkLanguage(command.preferredLanguage());
		if (!PasswordPolicy.isValid(command.initialPassword())) {
			throw new InvalidFieldException("initialPassword", PasswordPolicy.INVALID);
		}
		AppUser user = new AppUser(email, passwordEncoder.encode(command.initialPassword()),
				command.fullName().trim(), loadRole(command.role()));
		user.setPreferredLanguage(command.preferredLanguage());
		return users.save(user).getId();
	}

	/** Changes the account; {@code initialPassword} of the command is ignored here. */
	@Transactional
	public void update(Long id, UserCommand command) {
		AppUser user = loadWithRole(id);
		String email = normalizeEmail(command.email());
		if (users.existsByEmailIgnoreCaseAndIdNot(email, id)) {
			throw new InvalidFieldException("email", EMAIL_DUPLICATE);
		}
		checkLanguage(command.preferredLanguage());
		boolean self = id.equals(currentUser.currentUserId());
		boolean roleChanged = user.getRole().getName() != command.role();
		if (self && !command.active()) {
			throw new InvalidFieldException("active", SELF_DEACTIVATE);
		}
		if (self && roleChanged) {
			throw new InvalidFieldException("role", SELF_ROLE);
		}
		if (roleChanged && user.getRole().getName() == RoleName.SITE_MANAGER
				&& !sites.findByManager_IdOrderByNameAsc(id).isEmpty()) {
			throw new InvalidFieldException("role", ROLE_MANAGES_SITES);
		}
		user.setEmail(email);
		user.setFullName(command.fullName().trim());
		user.setPreferredLanguage(command.preferredLanguage());
		user.setActive(command.active());
		if (roleChanged) {
			user.setRole(loadRole(command.role()));
		}
	}

	/** Activate / deactivate from the list; returns the user's name for the flash message. */
	@Transactional
	public String setActive(Long id, boolean active) {
		AppUser user = loadWithRole(id);
		if (!active && id.equals(currentUser.currentUserId())) {
			throw new InvalidTransitionException(SELF_DEACTIVATE_FLASH, "user " + id + " cannot deactivate itself");
		}
		user.setActive(active);
		return user.getFullName();
	}

	/** Every site with its current manager, the ones of this user checked (user form). */
	public List<SiteAssignmentOption> siteOptions(Long userId) {
		return sites.findAllWithManager(Pageable.unpaged(Sort.by("name"))).stream()
			.map(site -> new SiteAssignmentOption(site.getId(), site.getCode(), site.getName(),
					site.getManager().getFullName(), site.getManager().getId().equals(userId)))
			.toList();
	}

	/**
	 * Makes the user the manager of the given sites (the sites move from their previous
	 * manager). The user must be an active SITE_MANAGER, and every site the user manages now
	 * must still be in the list: a site cannot be left without a manager.
	 */
	@Transactional
	public void assignSites(Long userId, Collection<Long> siteIds) {
		AppUser user = loadWithRole(userId);
		if (!user.isActive() || user.getRole().getName() != RoleName.SITE_MANAGER) {
			throw new InvalidFieldException("siteIds", SITES_NOT_MANAGER);
		}
		Set<Long> wanted = siteIds == null ? Set.of() : new HashSet<>(siteIds);
		boolean dropsASite = sites.findByManager_IdOrderByNameAsc(userId).stream()
			.anyMatch(site -> !wanted.contains(site.getId()));
		if (dropsASite) {
			throw new InvalidFieldException("siteIds", SITES_UNASSIGN);
		}
		Map<Long, Site> found = sites.findAllById(wanted).stream()
			.collect(Collectors.toMap(Site::getId, Function.identity()));
		for (Long siteId : wanted) {
			Site site = found.get(siteId);
			if (site == null) {
				throw new NotFoundException(Site.class, siteId);
			}
			site.setManager(user);
		}
	}

	// ------------------------------------------------------------------ own profile

	@PreAuthorize("isAuthenticated()")
	public UserView getProfile() {
		return toView(loadWithRole(currentUser.currentUserId()));
	}

	@PreAuthorize("isAuthenticated()")
	@Transactional
	public void updateProfile(ProfileCommand command) {
		checkLanguage(command.preferredLanguage());
		AppUser user = loadWithRole(currentUser.currentUserId());
		user.setFullName(command.fullName().trim());
		user.setPreferredLanguage(command.preferredLanguage());
	}

	/**
	 * Own password change. Field errors name the form field: wrong current password, new password
	 * outside the policy, same as the current one, confirmation different.
	 */
	@PreAuthorize("isAuthenticated()")
	@Transactional
	public void changePassword(PasswordChangeCommand command) {
		AppUser user = loadWithRole(currentUser.currentUserId());
		if (command.currentPassword() == null
				|| !passwordEncoder.matches(command.currentPassword(), user.getPasswordHash())) {
			throw new InvalidFieldException("currentPassword", CURRENT_PASSWORD_WRONG);
		}
		if (!PasswordPolicy.isValid(command.newPassword())) {
			throw new InvalidFieldException("newPassword", PasswordPolicy.INVALID);
		}
		if (!command.newPassword().equals(command.confirmPassword())) {
			throw new InvalidFieldException("confirmPassword", CONFIRM_MISMATCH);
		}
		if (command.newPassword().equals(command.currentPassword())) {
			throw new InvalidFieldException("newPassword", SAME_AS_CURRENT);
		}
		user.setPasswordHash(passwordEncoder.encode(command.newPassword()));
	}

	// ------------------------------------------------------------------ helpers

	private AppUser loadWithRole(Long id) {
		return users.findWithRoleById(id).orElseThrow(() -> new NotFoundException(AppUser.class, id));
	}

	private Role loadRole(RoleName name) {
		return roles.findByName(name).orElseThrow(() -> new InvalidFieldException("role", "validation.user.role.required"));
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase();
	}

	private static void checkLanguage(String language) {
		if (!LANGUAGES.contains(language)) {
			throw new InvalidFieldException("preferredLanguage", LANGUAGE_INVALID);
		}
	}

	private UserView toView(AppUser user) {
		List<ManagedSite> managed = user.getRole().getName() == RoleName.SITE_MANAGER
				? sites.findByManager_IdOrderByNameAsc(user.getId()).stream().map(ManagedSite::of).toList()
				: List.of();
		return new UserView(user.getId(), user.getEmail(), user.getFullName(), user.getRole().getName(),
				user.getPreferredLanguage(), user.isActive(), user.getId().equals(currentUser.currentUserId()),
				managed);
	}

}
