package hr.algebra.sitelog.user.service;

import java.nio.charset.StandardCharsets;

/**
 * Password rules shared by the forms ({@code @Size}) and the service (checked again, the service
 * is also called by the API). Length only - a long passphrase is stronger than a short password
 * with forced symbols. The maximum is BCrypt's input limit: it only uses the first 72 bytes, and
 * Spring Security's {@code BCryptPasswordEncoder} refuses longer input.
 */
public final class PasswordPolicy {

	public static final int MIN_LENGTH = 12;

	public static final int MAX_BYTES = 72;

	public static final String INVALID = "validation.password.policy";

	private PasswordPolicy() {
	}

	public static boolean isValid(String password) {
		return password != null && password.length() >= MIN_LENGTH
				&& password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
	}

}
