package hr.algebra.sitelog.crew.service;

/** Input of create/update crew, already validated for format by the web layer (or the API). */
public record CrewCommand(String name, String company, String trade, boolean active) {

	public CrewCommand {
		name = name == null ? null : name.trim();
		company = company == null ? null : company.trim();
		trade = trade == null ? null : trade.trim();
	}

}
