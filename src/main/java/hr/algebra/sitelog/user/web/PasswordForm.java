package hr.algebra.sitelog.user.web;

import hr.algebra.sitelog.user.service.PasswordChangeCommand;
import jakarta.validation.constraints.NotBlank;

/**
 * Backing object of the "Change password" card of {@code user/profile}. The checks that need the
 * stored hash (current password right, new one different) and the confirmation are done by
 * {@code UserService.changePassword} and come back as field errors.
 */
public class PasswordForm {

	@NotBlank(message = "{validation.password.current.required}")
	private String currentPassword;

	@PasswordLength
	private String newPassword;

	@NotBlank(message = "{validation.password.required}")
	private String confirmPassword;

	public PasswordChangeCommand toCommand() {
		return new PasswordChangeCommand(currentPassword, newPassword, confirmPassword);
	}

	/** Passwords are never sent back to the browser after a failed submit. */
	public void clear() {
		currentPassword = null;
		newPassword = null;
		confirmPassword = null;
	}

	public String getCurrentPassword() {
		return currentPassword;
	}

	public void setCurrentPassword(String currentPassword) {
		this.currentPassword = currentPassword;
	}

	public String getNewPassword() {
		return newPassword;
	}

	public void setNewPassword(String newPassword) {
		this.newPassword = newPassword;
	}

	public String getConfirmPassword() {
		return confirmPassword;
	}

	public void setConfirmPassword(String confirmPassword) {
		this.confirmPassword = confirmPassword;
	}

}
