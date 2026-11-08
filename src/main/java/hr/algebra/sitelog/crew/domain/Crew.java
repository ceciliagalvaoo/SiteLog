package hr.algebra.sitelog.crew.domain;

import hr.algebra.sitelog.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A subcontractor crew. Reference data: referenced by crew assignments and incidents, never
 * cascaded. A crew in use is deactivated instead of deleted ({@code active = false} removes it
 * from the selection lists but keeps history intact).
 */
@Entity
@Table(name = "crew")
public class Crew extends BaseEntity {

	@Column(name = "name", nullable = false, unique = true, length = 150)
	private String name;

	@Column(name = "company", nullable = false, length = 150)
	private String company;

	@Column(name = "trade", nullable = false, length = 100)
	private String trade;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	protected Crew() {
	}

	public Crew(String name, String company, String trade) {
		this.name = name;
		this.company = company;
		this.trade = trade;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCompany() {
		return company;
	}

	public void setCompany(String company) {
		this.company = company;
	}

	public String getTrade() {
		return trade;
	}

	public void setTrade(String trade) {
		this.trade = trade;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

}
