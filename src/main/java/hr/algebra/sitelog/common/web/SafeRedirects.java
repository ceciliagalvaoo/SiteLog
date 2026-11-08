package hr.algebra.sitelog.common.web;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;

/**
 * "Go back to where the user was" without an open redirect: only a path on this application is
 * accepted (from the {@code Referer} header of the same host, or an app-relative link).
 */
public final class SafeRedirects {

	private SafeRedirects() {
	}

	/** {@code redirect:} to the page the request came from, or to {@code fallback}. */
	public static String back(HttpServletRequest request, String fallback) {
		String referer = request.getHeader("Referer");
		if (referer != null) {
			try {
				URI uri = URI.create(referer);
				if (uri.getHost() == null || uri.getHost().equalsIgnoreCase(request.getServerName())) {
					String path = uri.getRawPath();
					String context = request.getContextPath();
					if (path != null && path.startsWith(context + "/")) {
						String local = path.substring(context.length());
						return "redirect:" + local + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
					}
				}
			}
			catch (IllegalArgumentException ignored) {
				// malformed header: use the fallback
			}
		}
		return "redirect:" + fallback;
	}

	/** True for an app-relative path such as {@code /daily-logs/42} (not {@code //host} or a full URL). */
	public static boolean isLocalPath(String link) {
		return link != null && link.startsWith("/") && !link.startsWith("//") && !link.contains("\\");
	}

}
