package hr.algebra.sitelog.dailylog.web;

/**
 * Parses the value of the {@code action} submit button of the nested forms, e.g.
 * {@code removeEntry-2} or {@code removeCrew-0-3}. Buttons let the user add and remove rows
 * without JavaScript: the form is posted, changed and re-rendered, nothing is saved.
 */
final class RowActions {

	static final String SAVE = "save";

	private RowActions() {
	}

	/** The n-th number after the action name ({@code removeCrew-0-3}: n=1 -> 0, n=2 -> 3); -1 if absent. */
	static int index(String action, int position) {
		String[] parts = action.split("-");
		if (parts.length <= position) {
			return -1;
		}
		try {
			return Integer.parseInt(parts[position]);
		}
		catch (NumberFormatException e) {
			return -1;
		}
	}

	static boolean isSave(String action) {
		return action == null || action.isBlank() || SAVE.equals(action);
	}

}
