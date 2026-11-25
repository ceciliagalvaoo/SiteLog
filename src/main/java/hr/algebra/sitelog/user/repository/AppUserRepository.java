package hr.algebra.sitelog.user.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import hr.algebra.sitelog.user.domain.AppUser;
import hr.algebra.sitelog.user.domain.RoleName;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

	/** Login lookup: the role is needed for the granted authority, so it is fetched in the same query. */
	@EntityGraph(attributePaths = "role")
	Optional<AppUser> findByEmailIgnoreCase(String email);

	/** The signed-in user (top bar, profile) and the user form: user and role in one query. */
	@EntityGraph(attributePaths = "role")
	@Query("select u from AppUser u where u.id = :id")
	Optional<AppUser> findWithRoleById(@Param("id") Long id);

	/**
	 * User administration list with an optional text filter on name or e-mail ({@code ''} for
	 * none); the role is shown on every row, so it is fetched in the same query (no N+1).
	 */
	@EntityGraph(attributePaths = "role")
	@Query("""
			select u from AppUser u
			where :text = ''
			   or lower(u.fullName) like lower(concat('%', :text, '%'))
			   or lower(u.email) like lower(concat('%', :text, '%'))""")
	Page<AppUser> searchWithRole(@Param("text") String text, Pageable pageable);

	/**
	 * Active users with a role, e.g. the site managers offered in the site form or the safety
	 * supervisors who receive REOPEN_REQUESTED / HIGH_SEVERITY_INCIDENT notifications.
	 */
	@EntityGraph(attributePaths = "role")
	List<AppUser> findByRole_NameAndActiveTrueOrderByFullNameAsc(RoleName roleName);

	/** Names for the revision history: {@code revinfo.username} stores the e-mail. */
	List<AppUser> findByEmailIn(Collection<String> emails);

	/** Assignee options of the corrective action form: active users with one of the roles, by name. */
	@EntityGraph(attributePaths = "role")
	List<AppUser> findByRole_NameInAndActiveTrueOrderByFullNameAsc(Collection<RoleName> roleNames);

	boolean existsByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

}
