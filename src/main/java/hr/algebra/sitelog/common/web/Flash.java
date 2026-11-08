package hr.algebra.sitelog.common.web;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Flash messages after a redirect (POST-Redirect-GET). Controllers store message KEYS, never
 * text; {@code fragments/flash :: messages} in the layout resolves them in the user's language.
 *
 * <p>Attribute names agreed with the layout: {@code flashSuccess} / {@code flashError} /
 * {@code flashInfo} (title key), {@code flashDetail} (optional body key) and {@code flashArgs}
 * (optional {@code Object[]} for {0}, {1}... in both keys).
 */
public final class Flash {

	public static final String SUCCESS = "flashSuccess";

	public static final String ERROR = "flashError";

	public static final String INFO = "flashInfo";

	public static final String DETAIL = "flashDetail";

	public static final String ARGS = "flashArgs";

	private Flash() {
	}

	public static void success(RedirectAttributes redirect, String titleKey, Object... args) {
		add(redirect, SUCCESS, titleKey, null, args);
	}

	public static void success(RedirectAttributes redirect, String titleKey, String detailKey, Object... args) {
		add(redirect, SUCCESS, titleKey, detailKey, args);
	}

	public static void error(RedirectAttributes redirect, String titleKey, Object... args) {
		add(redirect, ERROR, titleKey, null, args);
	}

	private static void add(RedirectAttributes redirect, String type, String titleKey, String detailKey,
			Object[] args) {
		redirect.addFlashAttribute(type, titleKey);
		if (detailKey != null) {
			redirect.addFlashAttribute(DETAIL, detailKey);
		}
		if (args != null && args.length > 0) {
			redirect.addFlashAttribute(ARGS, args);
		}
	}

}
