package hr.algebra.sitelog.api.site;

import java.time.LocalDate;

import hr.algebra.sitelog.common.validation.Chronological;
import hr.algebra.sitelog.site.service.SiteCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/sites} and {@code PUT /api/v1/sites/{id}}. Same constraints and
 * message keys as the site form ({@code SiteForm}), so the API and the screen report the same
 * errors in the same language.
 */
@Chronological(before = "startDate", after = "endDate", message = "{validation.site.endDate.beforeStart}")
@Schema(description = "Site to create or replace")
public record SiteRequest(
		@NotBlank(message = "{validation.site.code.required}")
		@Size(max = 20, message = "{validation.size.max}")
		@Schema(description = "Unique code (stored upper-case)", example = "ZG-04", maxLength = 20)
		String code,

		@NotBlank(message = "{validation.site.name.required}")
		@Size(max = 150, message = "{validation.size.max}")
		@Schema(example = "Residential block Lanište", maxLength = 150)
		String name,

		@NotBlank(message = "{validation.site.address.required}")
		@Size(max = 255, message = "{validation.size.max}")
		@Schema(example = "Ulica Damira Tomljanovića 12, Zagreb", maxLength = 255)
		String address,

		@Size(max = 1000, message = "{validation.size.max}")
		@Schema(nullable = true, maxLength = 1000)
		String description,

		@NotNull(message = "{validation.site.startDate.required}")
		@Schema(example = "2026-03-01")
		LocalDate startDate,

		@Schema(description = "Set it to finish the site (not before startDate)", nullable = true, example = "2027-06-30")
		LocalDate endDate,

		@NotNull(message = "{validation.site.manager.required}")
		@Schema(description = "Id of an active user with the SITE_MANAGER role", example = "2")
		Long managerId) {

	SiteCommand toCommand() {
		return new SiteCommand(code, name, address, description, startDate, endDate, managerId);
	}

}
