package hr.algebra.sitelog.user.domain;

import hr.algebra.sitelog.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Reference data inserted by V2; one row per {@link RoleName}. */
@Entity
@Table(name = "role")
public class Role extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "name", nullable = false, unique = true, length = 30)
	private RoleName name;

	protected Role() {
	}

	public RoleName getName() {
		return name;
	}

}
