package hr.algebra.sitelog.dailylog.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** "Request reopen" modal on a signed-off log: the mandatory reason and the version shown on the page. */
public class ReopenRequestForm {

	@NotBlank(message = "{validation.dailyLog.reopenReason.required}")
	@Size(max = 2000, message = "{validation.size.max}")
	private String reason;

	@NotNull
	private Long version;

	public static ReopenRequestForm forVersion(Long version) {
		ReopenRequestForm form = new ReopenRequestForm();
		form.version = version;
		return form;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

}
