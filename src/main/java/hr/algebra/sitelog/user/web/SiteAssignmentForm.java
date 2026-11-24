package hr.algebra.sitelog.user.web;

import java.util.ArrayList;
import java.util.List;

import hr.algebra.sitelog.user.service.SiteAssignmentOption;

/** Backing object of the "Assigned sites" card of {@code user/form}: the checked site ids. */
public class SiteAssignmentForm {

	private List<Long> siteIds = new ArrayList<>();

	public static SiteAssignmentForm from(List<SiteAssignmentOption> options) {
		SiteAssignmentForm form = new SiteAssignmentForm();
		form.siteIds = new ArrayList<>(options.stream().filter(SiteAssignmentOption::assigned)
			.map(SiteAssignmentOption::id).toList());
		return form;
	}

	public List<Long> getSiteIds() {
		return siteIds;
	}

	public void setSiteIds(List<Long> siteIds) {
		this.siteIds = siteIds == null ? new ArrayList<>() : siteIds;
	}

}
