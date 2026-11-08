package hr.algebra.sitelog.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import hr.algebra.sitelog.user.domain.Role;
import hr.algebra.sitelog.user.domain.RoleName;

public interface RoleRepository extends JpaRepository<Role, Long> {

	Optional<Role> findByName(RoleName name);

}
