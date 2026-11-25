package hr.algebra.sitelog.api.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One page of a collection. Our own record instead of serialising Spring Data's {@code PageImpl}
 * (its JSON shape is not a stable contract and Spring Data warns against exposing it).
 *
 * @param page zero-based page number
 * @param sort the applied order, e.g. {@code ["logDate,DESC"]}
 */
@Schema(description = "One page of a collection")
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages,
		boolean first, boolean last, List<String> sort) {

	public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
		return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast(),
				page.getSort().stream().map(order -> order.getProperty() + "," + order.getDirection()).toList());
	}

}
