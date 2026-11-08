package hr.algebra.sitelog.crew.web;

import hr.algebra.sitelog.crew.service.CrewCommand;
import hr.algebra.sitelog.crew.service.CrewView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Backing object of {@code crew/form} (create and edit). Messages are keys of messages.properties. */
public class CrewForm {

	@NotBlank(message = "{validation.crew.name.required}")
	@Size(max = 150, message = "{validation.size.max}")
	private String name;

	@NotBlank(message = "{validation.crew.company.required}")
	@Size(max = 150, message = "{validation.size.max}")
	private String company;

	@NotBlank(message = "{validation.crew.trade.required}")
	@Size(max = 100, message = "{validation.size.max}")
	private String trade;

	private boolean active = true;

	public static CrewForm from(CrewView crew) {
		CrewForm form = new CrewForm();
		form.name = crew.name();
		form.company = crew.company();
		form.trade = crew.trade();
		form.active = crew.active();
		return form;
	}

	public CrewCommand toCommand() {
		return new CrewCommand(name, company, trade, active);
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
