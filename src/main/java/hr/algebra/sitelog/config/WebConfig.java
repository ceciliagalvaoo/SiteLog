package hr.algebra.sitelog.config;

import java.time.Duration;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

/**
 * UI language (en / hr): {@code ?lang=hr} (language switcher in the user menu) is read by the
 * {@link LocaleChangeInterceptor} and stored in the {@code lang} cookie; without a cookie the
 * UI is English. M3 also applies the user's preferred language after login.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

	public static final String LANG_PARAM = "lang";

	@Bean
	LocaleResolver localeResolver() {
		CookieLocaleResolver resolver = new CookieLocaleResolver(LANG_PARAM);
		resolver.setDefaultLocale(Locale.ENGLISH);
		resolver.setCookieMaxAge(Duration.ofDays(365));
		return resolver;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
		interceptor.setParamName(LANG_PARAM);
		// a bad value such as ?lang=xx is ignored instead of failing the request
		interceptor.setIgnoreInvalidLocale(true);
		registry.addInterceptor(interceptor);
	}

}
