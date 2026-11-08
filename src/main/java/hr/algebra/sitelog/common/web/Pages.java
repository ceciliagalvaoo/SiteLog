package hr.algebra.sitelog.common.web;

import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Guards the {@link Pageable} built from request parameters ({@code ?page=&size=&sort=}):
 * only whitelisted properties can be sorted on (an unknown one would reach the JPQL query and
 * fail with a 500) and the page size is capped.
 */
public final class Pages {

	public static final int MAX_SIZE = 100;

	private Pages() {
	}

	public static Pageable restrict(Pageable requested, Set<String> sortable, Sort fallback) {
		Sort sort = Sort.by(requested.getSort().stream()
			.filter(order -> sortable.contains(order.getProperty()))
			.toList());
		if (sort.isUnsorted()) {
			sort = fallback;
		}
		int size = Math.min(Math.max(requested.getPageSize(), 1), MAX_SIZE);
		return PageRequest.of(Math.max(requested.getPageNumber(), 0), size, sort);
	}

}
