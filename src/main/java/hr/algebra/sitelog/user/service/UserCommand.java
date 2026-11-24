package hr.algebra.sitelog.user.service;

import hr.algebra.sitelog.user.domain.RoleName;

/**
 * Admin input for a user. {@code initialPassword} is used only on create (null on update);
 * {@code active} only on update (a new user starts active).
 */
public record UserCommand(String email, String fullName, RoleName role, String preferredLanguage, boolean active,
		String initialPassword) {
}
