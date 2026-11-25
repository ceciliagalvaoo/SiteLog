package hr.algebra.sitelog.api.dailylog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.domain.Weather;
import hr.algebra.sitelog.dailylog.service.CrewAssignmentView;
import hr.algebra.sitelog.dailylog.service.DailyLogDetails;
import hr.algebra.sitelog.dailylog.service.DailyLogHeader;
import hr.algebra.sitelog.dailylog.service.WorkEntryView;

/** Service views and request bodies shared by the daily log API slice tests. */
final class DailyLogApiFixtures {

	static final LocalDate DATE = LocalDate.of(2026, 11, 14);

	static final String CREATE_BODY = """
			{"logDate":"2026-11-14","weather":"CLOUDY","notes":"Rebar late",
			 "workEntries":[{"description":"Slab pour","location":"Block B","startTime":"07:00","endTime":"15:30",
			   "crewAssignments":[{"crewId":1,"hoursWorked":7.5,"workerCount":4,"task":"Formwork"}]}]}""";

	private DailyLogApiFixtures() {
	}

	static CrewAssignmentView row(Long id) {
		return new CrewAssignmentView(id, 1L, "Concrete crew A", "Concrete", true, new BigDecimal("7.50"), 4,
				"Formwork");
	}

	static WorkEntryView entry(Long id) {
		return new WorkEntryView(id, "Slab pour", "Block B", LocalTime.of(7, 0), LocalTime.of(15, 30),
				List.of(row(31L)));
	}

	static DailyLogDetails log(Long id, LogStatus status, long version) {
		ZonedDateTime signedAt = status == LogStatus.SIGNED_OFF
				? ZonedDateTime.of(2026, 11, 14, 17, 0, 0, 0, ZoneId.of("Europe/Zagreb")) : null;
		return new DailyLogDetails(id, 3L, "ZG-01", "Tower A", DATE, status, 0, Weather.CLOUDY, "Rebar late",
				signedAt == null ? null : "Marko Kovacevic", signedAt, version, status.isEditable(),
				status == LogStatus.DRAFT, List.of(entry(21L)));
	}

	static DailyLogHeader header(Long id, long version) {
		return new DailyLogHeader(id, 3L, "ZG-01", "Tower A", DATE, LogStatus.DRAFT, version);
	}

}
