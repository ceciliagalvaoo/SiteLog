package hr.algebra.sitelog.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import hr.algebra.sitelog.support.JpaFixtures;
import hr.algebra.sitelog.support.RepositoryTest;
import hr.algebra.sitelog.user.domain.AppUser;
import hr.algebra.sitelog.user.domain.Role;
import hr.algebra.sitelog.user.domain.RoleName;
import jakarta.persistence.EntityManager;

/** {@link AppUserRepository} and {@link RoleRepository}: e-mail lookup, uniqueness and lower case. */
@RepositoryTest
class AppUserRepositoryTests {

	private static final String HASH = "$2a$10$BaoKGdeDLDnVSb9ZnN9tNOzZgJYfoVbkeXRApfP6KkRiL/iP7MoqK";

	private final AppUserRepository users;
	private final RoleRepository roles;
	private final JdbcTemplate jdbc;
	private final JpaFixtures fixtures;

	@Autowired
	AppUserRepositoryTests(AppUserRepository users, RoleRepository roles, JdbcTemplate jdbc, EntityManager em) {
		this.users = users;
		this.roles = roles;
		this.jdbc = jdbc;
		this.fixtures = new JpaFixtures(em);
	}

	@Test
	void findByEmailIgnoreCase_findsUserWithRoleWhateverTheCase() {
		fixtures.flushAndClear();

		AppUser user = users.findByEmailIgnoreCase("MANAGER@SiteLog.Test").orElseThrow();

		assertThat(user.getEmail()).isEqualTo(JpaFixtures.MANAGER);
		assertThat(Hibernate.isInitialized(user.getRole())).isTrue();
		assertThat(user.getRole().getName()).isEqualTo(RoleName.SITE_MANAGER);
		assertThat(users.findByEmailIgnoreCase("nobody@sitelog.test")).isEmpty();
	}

	@Test
	void save_storesEmailInLowerCase() {
		AppUser user = users.saveAndFlush(new AppUser("New.User@SiteLog.TEST", HASH, "New User", role(RoleName.INSPECTOR)));

		assertThat(jdbc.queryForObject("SELECT email FROM app_user WHERE id = ?", String.class, user.getId()))
			.isEqualTo("new.user@sitelog.test");
		assertThat(users.existsByEmailIgnoreCase("NEW.USER@sitelog.test")).isTrue();
	}

	@Test
	void save_whenEmailDiffersOnlyInCase_isRejectedByUniqueConstraint() {
		AppUser duplicate = new AppUser("Supervisor@SITELOG.test", HASH, "Copy", role(RoleName.INSPECTOR));

		assertThatThrownBy(() -> users.saveAndFlush(duplicate))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uk_app_user_email");
	}

	@Test
	void insert_withUpperCaseEmailBypassingTheEntity_isRejectedByCheckConstraint() {
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO app_user (email, password_hash, full_name, role_id)
				SELECT 'Upper@SiteLog.test', ?, 'Upper', id FROM role WHERE name = 'INSPECTOR'""", HASH))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_app_user_email_lower");
	}

	@Test
	void findByRoleNameAndActiveTrue_returnsActiveUsersOfTheRoleByName() {
		AppUser inactive = new AppUser("old.manager@sitelog.test", HASH, "Aaron Old", role(RoleName.SITE_MANAGER));
		inactive.setActive(false);
		users.saveAndFlush(inactive);

		assertThat(users.findByRole_NameAndActiveTrueOrderByFullNameAsc(RoleName.SITE_MANAGER))
			.extracting(AppUser::getEmail)
			.containsExactly(JpaFixtures.MANAGER_SPLIT, JpaFixtures.MANAGER); // Luka Matić, Marko Kovačević
		assertThat(users.findByRole_NameAndActiveTrueOrderByFullNameAsc(RoleName.SAFETY_SUPERVISOR))
			.extracting(AppUser::getEmail)
			.containsExactly(JpaFixtures.SUPERVISOR);
	}

	@Test
	void roleFindByName_findsEveryRoleOfTheEnum() {
		for (RoleName name : RoleName.values()) {
			assertThat(roles.findByName(name)).get().extracting(Role::getName).isEqualTo(name);
		}
	}

	private Role role(RoleName name) {
		return roles.findByName(name).orElseThrow();
	}

}
