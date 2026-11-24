package hr.algebra.sitelog.user.web;

import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.service.UserCommand;
import hr.algebra.sitelog.user.service.UserView;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Backing object of {@code user/form} when editing a user ({@link NewUserForm} adds the initial password). */
public class UserForm {

	@NotBlank(message = "{validation.user.email.required}")
	@Email(message = "{validation.user.email.invalid}")
	@Size(max = 255, message = "{validation.size.max}")
	private String email;

	@NotBlank(message = "{validation.user.fullName.required}")
	@Size(max = 150, message = "{validation.size.max}")
	private String fullName;

	@NotNull(message = "{validation.user.role.required}")
	private RoleName role;

	@NotBlank(message = "{validation.user.language.invalid}")
	@Pattern(regexp = "en|hr", message = "{validation.user.language.invalid}")
	private String preferredLanguage = "en";

	private boolean active = true;

	public static UserForm from(UserView user) {
		UserForm form = new UserForm();
		form.email = user.email();
		form.fullName = user.fullName();
		form.role = user.role();
		form.preferredLanguage = user.preferredLanguage();
		form.active = user.active();
		return form;
	}

	public UserCommand toCommand() {
		return new UserCommand(email, fullName, role, preferredLanguage, active, null);
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public RoleName getRole() {
		return role;
	}

	public void setRole(RoleName role) {
		this.role = role;
	}

	public String getPreferredLanguage() {
		return preferredLanguage;
	}

	public void setPreferredLanguage(String preferredLanguage) {
		this.preferredLanguage = preferredLanguage;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

}
