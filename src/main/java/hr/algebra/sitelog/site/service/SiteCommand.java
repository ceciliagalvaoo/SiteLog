package hr.algebra.sitelog.site.service;

import java.time.LocalDate;

/** Input of create/update site (format already validated by the form or the API request). */
public record SiteCommand(String code, String name, String address, String description, LocalDate startDate,
		LocalDate endDate, Long managerId) {

	public SiteCommand {
		code = code == null ? null : code.trim().toUpperCase();
		name = name == null ? null : name.trim();
		address = address == null ? null : address.trim();
		description = description == null || description.isBlank() ? null : description.trim();
	}

}
