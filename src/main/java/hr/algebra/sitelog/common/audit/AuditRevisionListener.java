package hr.algebra.sitelog.common.audit;

import org.hibernate.envers.RevisionListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Fills {@link AuditRevision#getUsername()} from the Spring Security context of the thread that
 * runs the business transaction. Changes made without a logged-in user (scheduled jobs,
 * startup code) are recorded as {@value #SYSTEM_USER}.
 *
 * <p>Envers instantiates this class itself (it is not a Spring bean), which is why it reads the
 * static {@link SecurityContextHolder} instead of having anything injected.
 */
public class AuditRevisionListener implements RevisionListener {

	public static final String SYSTEM_USER = "system";

	@Override
	public void newRevision(Object revisionEntity) {
		((AuditRevision) revisionEntity).setUsername(currentUsername());
	}

	static String currentUsername() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			return SYSTEM_USER;
		}
		return authentication.getName();
	}

}
