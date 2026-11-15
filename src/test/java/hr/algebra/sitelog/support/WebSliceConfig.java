package hr.algebra.sitelog.support;

import java.time.Clock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import hr.algebra.sitelog.config.SecurityConfig;
import hr.algebra.sitelog.config.WebConfig;

/**
 * Extra beans for the {@code @WebMvcTest} slices: the application's security chain and locale
 * setup (so the slice behaves like the app: login required, CSRF on POST) and a fixed
 * {@link Clock} ({@code TimeConfig} is not part of the slice; {@code DailyLogController} needs one).
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({ SecurityConfig.class, WebConfig.class })
public class WebSliceConfig {

	@Bean
	Clock clock() {
		return TestEntities.fixedClock();
	}

}
