package hr.algebra.sitelog.user.service;

import static hr.algebra.sitelog.support.TestEntities.role;
import static hr.algebra.sitelog.support.TestEntities.site;
import static hr.algebra.sitelog.support.TestEntities.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.site.repository.SiteRepository;
import hr.algebra.sitelog.support.TestEntities;
import hr.algebra.sitelog.user.domain.AppUser;
import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.repository.AppUserRepository;
import hr.algebra.sitelog.user.repository.RoleRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

	private static final Long ADMIN_ID = 1L;

	private static final String STRONG = "correct-horse-battery";

	@Mock
	AppUserRepository users;

	@Mock
	RoleRepository roles;

	@Mock
	SiteRepository sites;

	@Mock
	PasswordEncoder passwordEncoder;

	@Mock
	CurrentUserProvider currentUser;

	UserService service;

	@BeforeEach
	void setUp() {
		service = new UserService(users, roles, sites, passwordEncoder, currentUser);
	}

	private void signedInAs(Long id) {
		when(currentUser.currentUserId()).thenReturn(id);
	}

	private static InvalidFieldException fieldError(Runnable call) {
		try {
			call.run();
		}
		catch (InvalidFieldException e) {
			return e;
		}
		throw new AssertionError("expected InvalidFieldException");
	}

	private static UserCommand command(String email, RoleName role, boolean active, String password) {
		return new UserCommand(email, " Iva Perić ", role, "hr", active, password);
	}

	// ------------------------------------------------------------------ list / get

	@Test
	void list_trimsTheFilterAndMapsRows() {
		Pageable page = PageRequest.of(0, 20);
		when(users.searchWithRole("ana", page))
			.thenReturn(new PageImpl<>(List.of(user(1L, "Ana Horvat", RoleName.ADMIN, true))));

		assertThat(service.list("  ana ", page).getContent())
			.containsExactly(new UserListItem(1L, "ana.horvat@sitelog.test", "Ana Horvat", RoleName.ADMIN, "en", true));
	}

	@Test
	void list_withoutFilter_usesEmptyText() {
		Pageable page = PageRequest.of(0, 20);
		when(users.searchWithRole("", page)).thenReturn(new PageImpl<>(List.of()));

		assertThat(service.list(null, page)).isEmpty();
	}

	@Test
	void get_siteManagerComesWithManagedSites() {
		AppUser manager = user(7L, "Marko Kovacevic", RoleName.SITE_MANAGER, true);
		when(users.findWithRoleById(7L)).thenReturn(Optional.of(manager));
		when(sites.findByManager_IdOrderByNameAsc(7L)).thenReturn(List.of(site(3L, "ZG-01", manager)));
		signedInAs(ADMIN_ID);

		UserView view = service.get(7L);

		assertThat(view.siteManager()).isTrue();
		assertThat(view.self()).isFalse();
		assertThat(view.managedSites()).containsExactly(new ManagedSite(3L, "ZG-01", "Site ZG-01"));
	}

	@Test
	void get_unknownUser_isNotFound() {
		when(users.findWithRoleById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(99L)).isInstanceOf(NotFoundException.class);
	}

	// ------------------------------------------------------------------ create

	@Test
	void create_hashesTheInitialPasswordAndNormalisesInput() {
		when(users.existsByEmailIgnoreCase("iva@sitelog.test")).thenReturn(false);
		when(roles.findByName(RoleName.INSPECTOR)).thenReturn(Optional.of(role(RoleName.INSPECTOR)));
		when(passwordEncoder.encode(STRONG)).thenReturn("$2a$10$hash");
		when(users.save(any(AppUser.class))).thenAnswer(inv -> TestEntities.withId((AppUser) inv.getArgument(0), 42L));

		Long id = service.create(command(" Iva@SiteLog.test ", RoleName.INSPECTOR, true, STRONG));

		ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
		verify(users).save(saved.capture());
		assertThat(id).isEqualTo(42L);
		assertThat(saved.getValue().getEmail()).isEqualTo("iva@sitelog.test");
		assertThat(saved.getValue().getFullName()).isEqualTo("Iva Perić");
		assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$10$hash");
		assertThat(saved.getValue().getPreferredLanguage()).isEqualTo("hr");
		assertThat(saved.getValue().isActive()).isTrue();
	}

	@Test
	void create_duplicateEmail_isAFieldError() {
		when(users.existsByEmailIgnoreCase("iva@sitelog.test")).thenReturn(true);

		assertThat(fieldError(() -> service.create(command("iva@sitelog.test", RoleName.INSPECTOR, true, STRONG))))
			.extracting(InvalidFieldException::getField, InvalidFieldException::getMessageKey)
			.containsExactly("email", UserService.EMAIL_DUPLICATE);
		verify(users, never()).save(any());
	}

	@Test
	void create_shortPassword_isRefused() {
		assertThat(fieldError(() -> service.create(command("iva@sitelog.test", RoleName.INSPECTOR, true, "short"))))
			.extracting(InvalidFieldException::getField).isEqualTo("initialPassword");
	}

	@Test
	void create_passwordLongerThanBcryptAccepts_isRefused() {
		assertThat(fieldError(() -> service.create(command("iva@sitelog.test", RoleName.INSPECTOR, true,
				"ž".repeat(40))))).extracting(InvalidFieldException::getMessageKey).isEqualTo(PasswordPolicy.INVALID);
	}

	@Test
	void create_unknownLanguage_isRefused() {
		UserCommand german = new UserCommand("iva@sitelog.test", "Iva", RoleName.INSPECTOR, "de", true, STRONG);

		assertThat(fieldError(() -> service.create(german))).extracting(InvalidFieldException::getField)
			.isEqualTo("preferredLanguage");
	}

	@Test
	void create_unknownRole_isAFieldError() {
		when(roles.findByName(RoleName.INSPECTOR)).thenReturn(Optional.empty());
		when(passwordEncoder.encode(STRONG)).thenReturn("$2a$10$hash");

		assertThat(fieldError(() -> service.create(command("iva@sitelog.test", RoleName.INSPECTOR, true, STRONG))))
			.extracting(InvalidFieldException::getField).isEqualTo("role");
	}

	// ------------------------------------------------------------------ update

	@Test
	void update_changesAccountAndRole() {
		AppUser inspector = user(5L, "Tomislav Novak", RoleName.INSPECTOR, true);
		when(users.findWithRoleById(5L)).thenReturn(Optional.of(inspector));
		when(roles.findByName(RoleName.SAFETY_SUPERVISOR)).thenReturn(Optional.of(role(RoleName.SAFETY_SUPERVISOR)));
		signedInAs(ADMIN_ID);

		service.update(5L, command("Tomislav@sitelog.test", RoleName.SAFETY_SUPERVISOR, false, null));

		assertThat(inspector.getEmail()).isEqualTo("tomislav@sitelog.test");
		assertThat(inspector.getFullName()).isEqualTo("Iva Perić");
		assertThat(inspector.getRole().getName()).isEqualTo(RoleName.SAFETY_SUPERVISOR);
		assertThat(inspector.isActive()).isFalse();
		assertThat(inspector.getPreferredLanguage()).isEqualTo("hr");
	}

	@Test
	void update_emailOfAnotherUser_isAFieldError() {
		when(users.findWithRoleById(5L)).thenReturn(Optional.of(user(5L, "Tomislav Novak", RoleName.INSPECTOR, true)));
		when(users.existsByEmailIgnoreCaseAndIdNot("taken@sitelog.test", 5L)).thenReturn(true);

		assertThat(fieldError(() -> service.update(5L, command("taken@sitelog.test", RoleName.INSPECTOR, true, null))))
			.extracting(InvalidFieldException::getMessageKey).isEqualTo(UserService.EMAIL_DUPLICATE);
	}

	@Test
	void update_adminCannotDeactivateOwnAccount() {
		when(users.findWithRoleById(ADMIN_ID)).thenReturn(Optional.of(user(ADMIN_ID, "Ana Horvat", RoleName.ADMIN, true)));
		signedInAs(ADMIN_ID);

		assertThat(fieldError(() -> service.update(ADMIN_ID, command("ana@sitelog.test", RoleName.ADMIN, false, null))))
			.extracting(InvalidFieldException::getField, InvalidFieldException::getMessageKey)
			.containsExactly("active", UserService.SELF_DEACTIVATE);
	}

	@Test
	void update_adminCannotChangeOwnRole() {
		when(users.findWithRoleById(ADMIN_ID)).thenReturn(Optional.of(user(ADMIN_ID, "Ana Horvat", RoleName.ADMIN, true)));
		signedInAs(ADMIN_ID);

		assertThat(fieldError(() -> service.update(ADMIN_ID, command("ana@sitelog.test", RoleName.INSPECTOR, true, null))))
			.extracting(InvalidFieldException::getMessageKey).isEqualTo(UserService.SELF_ROLE);
	}

	@Test
	void update_managerWithSitesKeepsTheRole() {
		AppUser manager = user(7L, "Marko Kovacevic", RoleName.SITE_MANAGER, true);
		when(users.findWithRoleById(7L)).thenReturn(Optional.of(manager));
		when(sites.findByManager_IdOrderByNameAsc(7L)).thenReturn(List.of(site(3L, "ZG-01", manager)));
		signedInAs(ADMIN_ID);

		assertThat(fieldError(() -> service.update(7L, command("marko@sitelog.test", RoleName.INSPECTOR, true, null))))
			.extracting(InvalidFieldException::getMessageKey).isEqualTo(UserService.ROLE_MANAGES_SITES);
	}

	// ------------------------------------------------------------------ activate / deactivate

	@Test
	void setActive_deactivatesAnotherUser() {
		AppUser inspector = user(5L, "Tomislav Novak", RoleName.INSPECTOR, true);
		when(users.findWithRoleById(5L)).thenReturn(Optional.of(inspector));
		signedInAs(ADMIN_ID);

		assertThat(service.setActive(5L, false)).isEqualTo("Tomislav Novak");
		assertThat(inspector.isActive()).isFalse();
	}

	@Test
	void setActive_reactivatesWithoutCheckingTheCaller() {
		AppUser inspector = user(5L, "Tomislav Novak", RoleName.INSPECTOR, false);
		when(users.findWithRoleById(5L)).thenReturn(Optional.of(inspector));

		service.setActive(5L, true);

		assertThat(inspector.isActive()).isTrue();
	}

	@Test
	void setActive_selfDeactivationIsRefused() {
		when(users.findWithRoleById(ADMIN_ID)).thenReturn(Optional.of(user(ADMIN_ID, "Ana Horvat", RoleName.ADMIN, true)));
		signedInAs(ADMIN_ID);

		assertThatThrownBy(() -> service.setActive(ADMIN_ID, false)).isInstanceOf(InvalidTransitionException.class)
			.extracting("messageKey").isEqualTo(UserService.SELF_DEACTIVATE_FLASH);
	}

	// ------------------------------------------------------------------ site assignment

	@Test
	void siteOptions_marksTheUsersSites() {
		AppUser marko = user(7L, "Marko Kovacevic", RoleName.SITE_MANAGER, true);
		AppUser luka = user(8L, "Luka Matic", RoleName.SITE_MANAGER, true);
		when(sites.findAllWithManager(any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(site(3L, "ZG-01", marko), site(4L, "ST-01", luka))));

		assertThat(service.siteOptions(7L)).containsExactly(
				new SiteAssignmentOption(3L, "ZG-01", "Site ZG-01", "Marko Kovacevic", true),
				new SiteAssignmentOption(4L, "ST-01", "Site ST-01", "Luka Matic", false));
	}

	@Test
	void assignSites_movesSitesToTheManager() {
		AppUser marko = user(7L, "Marko Kovacevic", RoleName.SITE_MANAGER, true);
		AppUser luka = user(8L, "Luka Matic", RoleName.SITE_MANAGER, true);
		Site zg01 = site(3L, "ZG-01", marko);
		Site st01 = site(4L, "ST-01", luka);
		when(users.findWithRoleById(7L)).thenReturn(Optional.of(marko));
		when(sites.findByManager_IdOrderByNameAsc(7L)).thenReturn(List.of(zg01));
		when(sites.findAllById(Set.of(3L, 4L))).thenReturn(List.of(zg01, st01));

		service.assignSites(7L, List.of(3L, 4L));

		assertThat(st01.getManager()).isSameAs(marko);
		assertThat(zg01.getManager()).isSameAs(marko);
	}

	@Test
	void assignSites_cannotLeaveASiteWithoutManager() {
		AppUser marko = user(7L, "Marko Kovacevic", RoleName.SITE_MANAGER, true);
		when(users.findWithRoleById(7L)).thenReturn(Optional.of(marko));
		when(sites.findByManager_IdOrderByNameAsc(7L)).thenReturn(List.of(site(3L, "ZG-01", marko)));

		assertThat(fieldError(() -> service.assignSites(7L, null)))
			.extracting(InvalidFieldException::getMessageKey).isEqualTo(UserService.SITES_UNASSIGN);
	}

	@Test
	void assignSites_onlyForActiveSiteManagers() {
		when(users.findWithRoleById(5L)).thenReturn(Optional.of(user(5L, "Tomislav Novak", RoleName.INSPECTOR, true)));
		when(users.findWithRoleById(9L)).thenReturn(Optional.of(user(9L, "Old Manager", RoleName.SITE_MANAGER, false)));

		assertThat(fieldError(() -> service.assignSites(5L, List.of(3L))))
			.extracting(InvalidFieldException::getMessageKey).isEqualTo(UserService.SITES_NOT_MANAGER);
		assertThat(fieldError(() -> service.assignSites(9L, List.of(3L))))
			.extracting(InvalidFieldException::getMessageKey).isEqualTo(UserService.SITES_NOT_MANAGER);
	}

	@Test
	void assignSites_unknownSite_isNotFound() {
		AppUser marko = user(7L, "Marko Kovacevic", RoleName.SITE_MANAGER, true);
		when(users.findWithRoleById(7L)).thenReturn(Optional.of(marko));
		when(sites.findByManager_IdOrderByNameAsc(7L)).thenReturn(List.of());
		when(sites.findAllById(Set.of(99L))).thenReturn(List.of());

		assertThatThrownBy(() -> service.assignSites(7L, List.of(99L))).isInstanceOf(NotFoundException.class);
	}

	// ------------------------------------------------------------------ own profile

	@Test
	void getProfile_isTheSignedInUser() {
		when(users.findWithRoleById(5L)).thenReturn(Optional.of(user(5L, "Tomislav Novak", RoleName.INSPECTOR, true)));
		signedInAs(5L);

		UserView profile = service.getProfile();

		assertThat(profile.self()).isTrue();
		assertThat(profile.managedSites()).isEmpty();
	}

	@Test
	void updateProfile_changesNameAndLanguage() {
		AppUser me = user(5L, "Tomislav Novak", RoleName.INSPECTOR, true);
		when(users.findWithRoleById(5L)).thenReturn(Optional.of(me));
		signedInAs(5L);

		service.updateProfile(new ProfileCommand("  Tomo Novak ", "hr"));

		assertThat(me.getFullName()).isEqualTo("Tomo Novak");
		assertThat(me.getPreferredLanguage()).isEqualTo("hr");
	}

	@Test
	void updateProfile_unknownLanguage_isRefused() {
		assertThat(fieldError(() -> service.updateProfile(new ProfileCommand("Tomo", "fr"))))
			.extracting(InvalidFieldException::getField).isEqualTo("preferredLanguage");
	}

	private AppUser signedInWithPassword() {
		AppUser me = user(5L, "Tomislav Novak", RoleName.INSPECTOR, true);
		me.setPasswordHash("$2a$10$old");
		when(users.findWithRoleById(5L)).thenReturn(Optional.of(me));
		signedInAs(5L);
		return me;
	}

	@Test
	void changePassword_storesTheNewHash() {
		AppUser me = signedInWithPassword();
		when(passwordEncoder.matches("old-password-123", "$2a$10$old")).thenReturn(true);
		when(passwordEncoder.encode(STRONG)).thenReturn("$2a$10$new");

		service.changePassword(new PasswordChangeCommand("old-password-123", STRONG, STRONG));

		assertThat(me.getPasswordHash()).isEqualTo("$2a$10$new");
	}

	@Test
	void changePassword_wrongCurrentPassword() {
		signedInWithPassword();
		when(passwordEncoder.matches("guess", "$2a$10$old")).thenReturn(false);

		assertThat(fieldError(() -> service.changePassword(new PasswordChangeCommand("guess", STRONG, STRONG))))
			.extracting(InvalidFieldException::getField, InvalidFieldException::getMessageKey)
			.containsExactly("currentPassword", UserService.CURRENT_PASSWORD_WRONG);
	}

	@Test
	void changePassword_missingCurrentPassword() {
		signedInWithPassword();

		assertThat(fieldError(() -> service.changePassword(new PasswordChangeCommand(null, STRONG, STRONG))))
			.extracting(InvalidFieldException::getField).isEqualTo("currentPassword");
	}

	@Test
	void changePassword_tooShort() {
		signedInWithPassword();
		when(passwordEncoder.matches("old-password-123", "$2a$10$old")).thenReturn(true);

		assertThat(fieldError(() -> service.changePassword(new PasswordChangeCommand("old-password-123", "short", "short"))))
			.extracting(InvalidFieldException::getField).isEqualTo("newPassword");
	}

	@Test
	void changePassword_confirmationMustMatch() {
		signedInWithPassword();
		when(passwordEncoder.matches("old-password-123", "$2a$10$old")).thenReturn(true);

		assertThat(fieldError(() -> service.changePassword(new PasswordChangeCommand("old-password-123", STRONG,
				STRONG + "x")))).extracting(InvalidFieldException::getField, InvalidFieldException::getMessageKey)
			.containsExactly("confirmPassword", UserService.CONFIRM_MISMATCH);
	}

	@Test
	void changePassword_mustDifferFromTheCurrentOne() {
		signedInWithPassword();
		when(passwordEncoder.matches(STRONG, "$2a$10$old")).thenReturn(true);

		assertThat(fieldError(() -> service.changePassword(new PasswordChangeCommand(STRONG, STRONG, STRONG))))
			.extracting(InvalidFieldException::getMessageKey).isEqualTo(UserService.SAME_AS_CURRENT);
	}

}
