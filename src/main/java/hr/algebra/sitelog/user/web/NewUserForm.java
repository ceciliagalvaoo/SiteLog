package hr.algebra.sitelog.user.web;

import hr.algebra.sitelog.user.service.UserCommand;

/** Backing object of {@code user/form} for a new user: the account fields plus the initial password. */
public class NewUserForm extends UserForm {

	@PasswordLength
	private String initialPassword;

	@Override
	public UserCommand toCommand() {
		return new UserCommand(getEmail(), getFullName(), getRole(), getPreferredLanguage(), true, initialPassword);
	}

	public String getInitialPassword() {
		return initialPassword;
	}

	public void setInitialPassword(String initialPassword) {
		this.initialPassword = initialPassword;
	}

}
