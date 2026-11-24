package hr.algebra.sitelog.user.web;

import hr.algebra.sitelog.user.service.ProfileCommand;
import hr.algebra.sitelog.user.service.UserView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Backing object of the "Account details" card of {@code user/profile}. */
public class ProfileForm {

	@NotBlank(message = "{validation.user.fullName.required}")
	@Size(max = 150, message = "{validation.size.max}")
	private String fullName;

	@NotBlank(message = "{validation.user.language.invalid}")
	@Pattern(regexp = "en|hr", message = "{validation.user.language.invalid}")
	private String preferredLanguage = "en";

	public static ProfileForm from(UserView user) {
		ProfileForm form = new ProfileForm();
		form.fullName = user.fullName();
		form.preferredLanguage = user.preferredLanguage();
		return form;
	}

	public ProfileCommand toCommand() {
		return new ProfileCommand(fullName, preferredLanguage);
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public String getPreferredLanguage() {
		return preferredLanguage;
	}

	public void setPreferredLanguage(String preferredLanguage) {
		this.preferredLanguage = preferredLanguage;
	}

}
