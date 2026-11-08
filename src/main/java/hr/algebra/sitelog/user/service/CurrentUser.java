package hr.algebra.sitelog.user.service;

import java.util.Arrays;
import java.util.stream.Collectors;

import hr.algebra.sitelog.user.domain.RoleName;

/**
 * Snapshot of the signed-in user, safe to keep outside a transaction and to put in the model
 * (the top bar shows {@code fullName}, {@code initials} and the role).
 */
public record CurrentUser(Long id, String email, String fullName, RoleName role) {

	/** "Marko Kovačević" -> "MK"; at most two letters. */
	public String initials() {
		return Arrays.stream(fullName.trim().split("\\s+"))
			.filter(part -> !part.isEmpty())
			.limit(2)
			.map(part -> part.substring(0, 1).toUpperCase())
			.collect(Collectors.joining());
	}

}
