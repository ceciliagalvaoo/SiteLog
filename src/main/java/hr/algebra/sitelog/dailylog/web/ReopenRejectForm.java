package hr.algebra.sitelog.dailylog.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** "Reject reopen" modal of the safety supervisor: the reason is mandatory. */
public class ReopenRejectForm {

	@NotBlank(message = "{validation.reopen.rejectReason.required}")
	@Size(max = 2000, message = "{validation.size.max}")
	private String reason;

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

}
