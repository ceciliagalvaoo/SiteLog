package hr.algebra.sitelog.site.service;

import java.time.LocalDate;

/**
 * One row of the sites list. {@code finished} is derived from the end date (there is no site
 * status column): a site whose end date has been reached shows as "Finished".
 */
public record SiteListItem(Long id, String code, String name, String address, LocalDate startDate,
		LocalDate endDate, String managerName, boolean finished) {
}
