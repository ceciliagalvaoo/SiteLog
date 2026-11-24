package hr.algebra.sitelog.user.service;

import hr.algebra.sitelog.site.domain.Site;

/** A site a site manager is responsible for (profile page, user form). */
public record ManagedSite(Long id, String code, String name) {

	static ManagedSite of(Site site) {
		return new ManagedSite(site.getId(), site.getCode(), site.getName());
	}

}
