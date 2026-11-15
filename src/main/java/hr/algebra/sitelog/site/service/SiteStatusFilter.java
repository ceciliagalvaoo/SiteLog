package hr.algebra.sitelog.site.service;

import java.util.Locale;

/**
 * "Project status" filter of the sites list. There is no status column: a site is finished once
 * its end date is reached (same rule as {@link SiteListItem#finished()}).
 */
public enum SiteStatusFilter {

	ALL,
	ACTIVE,
	FINISHED;

	/** Lenient parsing of the request parameter: an unknown or missing value means {@link #ALL}. */
	public static SiteStatusFilter fromParam(String value) {
		if (value == null || value.isBlank()) {
			return ALL;
		}
		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			return ALL;
		}
	}

}
