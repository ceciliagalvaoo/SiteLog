package hr.algebra.sitelog.site.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import hr.algebra.sitelog.common.validation.Chronological;
import hr.algebra.sitelog.site.service.SiteCommand;
import hr.algebra.sitelog.site.service.SiteDetails;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Backing object of {@code site/form} (New site / Site settings). The end date rule is the same
 * as {@code ck_site_dates}; its error is shown on the {@code endDate} field.
 */
@Chronological(before = "startDate", after = "endDate", message = "{validation.site.endDate.beforeStart}")
public class SiteForm {

	@NotBlank(message = "{validation.site.code.required}")
	@Size(max = 20, message = "{validation.size.max}")
	private String code;

	@NotBlank(message = "{validation.site.name.required}")
	@Size(max = 150, message = "{validation.size.max}")
	private String name;

	@NotBlank(message = "{validation.site.address.required}")
	@Size(max = 255, message = "{validation.size.max}")
	private String address;

	@Size(max = 1000, message = "{validation.size.max}")
	private String description;

	@NotNull(message = "{validation.site.startDate.required}")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate startDate;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate endDate;

	@NotNull(message = "{validation.site.manager.required}")
	private Long managerId;

	public static SiteForm from(SiteDetails site) {
		SiteForm form = new SiteForm();
		form.code = site.code();
		form.name = site.name();
		form.address = site.address();
		form.description = site.description();
		form.startDate = site.startDate();
		form.endDate = site.endDate();
		form.managerId = site.managerId();
		return form;
	}

	public SiteCommand toCommand() {
		return new SiteCommand(code, name, address, description, startDate, endDate, managerId);
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

	public Long getManagerId() {
		return managerId;
	}

	public void setManagerId(Long managerId) {
		this.managerId = managerId;
	}

}
