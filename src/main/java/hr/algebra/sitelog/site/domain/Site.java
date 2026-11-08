package hr.algebra.sitelog.site.domain;

import java.time.LocalDate;

import hr.algebra.sitelog.common.domain.BaseEntity;
import hr.algebra.sitelog.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A construction site. Reference data managed by the admin; daily logs and incidents point to
 * it, but it never cascades to them (a site with logs or incidents cannot be deleted, see
 * ADR-0001 item 3).
 */
@Entity
@Table(name = "site")
public class Site extends BaseEntity {

	@Column(name = "code", nullable = false, unique = true, length = 20)
	private String code;

	@Column(name = "name", nullable = false, length = 150)
	private String name;

	@Column(name = "address", nullable = false)
	private String address;

	@Column(name = "description", length = 1000)
	private String description;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	/** Optional; when set it is on or after {@link #startDate} ({@code ck_site_dates}). */
	@Column(name = "end_date")
	private LocalDate endDate;

	/** The site manager responsible for the site (rule "own site" for SITE_MANAGER). */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "manager_id", nullable = false)
	private AppUser manager;

	protected Site() {
	}

	public Site(String code, String name, String address, LocalDate startDate, AppUser manager) {
		this.code = code;
		this.name = name;
		this.address = address;
		this.startDate = startDate;
		this.manager = manager;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public void setEndDate(LocalDate endDate) {
		this.endDate = endDate;
	}

	public AppUser getManager() {
		return manager;
	}

	public void setManager(AppUser manager) {
		this.manager = manager;
	}

}
