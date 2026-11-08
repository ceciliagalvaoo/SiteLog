package hr.algebra.sitelog.common.time;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One {@link Clock} for the whole application: services take "now" and "today" from it, so tests
 * can fix the time, and dates (today's log, overdue checks) use the sites' time zone instead of
 * the server's (Render runs in UTC).
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

	@Bean
	Clock clock(@Value("${sitelog.time-zone}") ZoneId zone) {
		return Clock.system(zone);
	}

}
