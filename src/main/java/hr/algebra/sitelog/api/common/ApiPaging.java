package hr.algebra.sitelog.api.common;

import java.util.Set;
import java.util.TreeSet;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import hr.algebra.sitelog.common.web.Pages;

/**
 * Limits of the {@code ?page=&size=&sort=} parameters of the API collections. Same rules as
 * the screens ({@link Pages#restrict}: whitelisted sort properties, size capped at
 * {@value Pages#MAX_SIZE}), with one difference: a screen silently ignores an unknown sort
 * property, the API answers 400 so a client learns about its typo instead of getting a
 * different order than it asked for.
 */
public final class ApiPaging {

	private ApiPaging() {
	}

	public static Pageable restrict(Pageable requested, Set<String> sortable, Sort fallback) {
		requested.getSort().forEach(order -> {
			if (!sortable.contains(order.getProperty())) {
				throw new InvalidPageRequestException("Cannot sort by '" + order.getProperty()
						+ "'. Sortable properties: " + String.join(", ", new TreeSet<>(sortable)) + ".");
			}
		});
		return Pages.restrict(requested, sortable, fallback);
	}

}
