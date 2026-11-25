package hr.algebra.sitelog.api.reopen;

import java.time.OffsetDateTime;
import java.util.List;

import hr.algebra.sitelog.dailylog.service.EntityChange;
import hr.algebra.sitelog.dailylog.service.FieldChange;
import hr.algebra.sitelog.dailylog.service.RevisionView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One Envers revision (one committed transaction) that touched the daily log or its children,
 * with the before / after value of every changed field.
 */
@Schema(description = "Envers revision of a daily log aggregate")
public record RevisionResponse(
		@Schema(description = "Envers revision number (revinfo)") Long revision,
		OffsetDateTime changedAt,
		@Schema(description = "E-mail of the user, or 'system'") String username,
		@Schema(nullable = true) String userName,
		List<Change> changes) {

	/** What the revision did to one row of the aggregate. */
	public record Change(
			@Schema(description = "DAILY_LOG, WORK_ENTRY or CREW_ASSIGNMENT") String entity,
			Long entityId,
			@Schema(description = "ADDED, MODIFIED or DELETED") String changeType,
			@Schema(nullable = true, description = "Work entry description or crew name; null for the log itself") String label,
			List<Field> fields) {

		static Change of(EntityChange change) {
			return new Change(change.entity(), change.entityId(), change.changeType(), change.label(),
					change.fields().stream().map(Field::of).toList());
		}

	}

	/**
	 * One changed field. {@code field} is the message key of its label (the screen translates it);
	 * for an enum value {@code valueKeyPrefix} is set (e.g. {@code weather.}).
	 */
	public record Field(
			@Schema(example = "dailyLog.field.weather") String field,
			@Schema(nullable = true, example = "SUNNY") Object before,
			@Schema(nullable = true, example = "RAIN") Object after,
			@Schema(nullable = true, example = "weather.") String valueKeyPrefix) {

		static Field of(FieldChange change) {
			return new Field(change.labelKey(), change.before(), change.after(), change.valueKeyPrefix());
		}

	}

	static RevisionResponse of(RevisionView revision) {
		return new RevisionResponse(revision.revision(), revision.changedAt().toOffsetDateTime(), revision.username(),
				revision.userName(), revision.changes().stream().map(Change::of).toList());
	}

}
