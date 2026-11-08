package hr.algebra.sitelog.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import hr.algebra.sitelog.user.domain.AppUser;
import hr.algebra.sitelog.user.domain.RoleName;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

	/** Login lookup: the role is needed for the granted authority, so it is fetched in the same query. */
	@EntityGraph(attributePaths = "role")
	Optional<AppUser> findByEmailIgnoreCase(String email);

	/**
	 * Active users with a role, e.g. the site managers offered in the site form or the safety
	 * supervisors who receive REOPEN_REQUESTED / HIGH_SEVERITY_INCIDENT notifications.
	 */
	@EntityGraph(attributePaths = "role")
	List<AppUser> findByRole_NameAndActiveTrueOrderByFullNameAsc(RoleName roleName);

	boolean existsByEmailIgnoreCase(String email);

}
