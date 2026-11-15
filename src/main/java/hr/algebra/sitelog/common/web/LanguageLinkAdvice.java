package hr.algebra.sitelog.common.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Model attribute {@code langUrls} for the language switcher in {@code fragments/navbar}:
 * one relative link per supported language that keeps the current query string
 * ({@code ?tab=logs}, {@code ?status=}, {@code page}, {@code sort}...) and only replaces
 * {@code lang} (a null query string gives {@code ?lang=xx}), e.g. {@code /sites?status=finished} -> {@code ?status=finished&lang=hr}.
 *
 * <p>The link is relative ({@code ?...}), so the browser keeps the current path. The query is
 * copied as the browser sent it (already encoded), without decoding or re-encoding. Not limited to a
 * package, so the error pages get it too; the template falls back to {@code ?lang=xx} without it.
 */
@ControllerAdvice
public class LanguageLinkAdvice {

	/** Same parameter as the {@code LocaleChangeInterceptor} in {@code WebConfig}. */
	static final String LANG_PARAM = "lang";

	static final List<String> LANGUAGES = List.of("en", "hr");

	@ModelAttribute("langUrls")
	public Map<String, String> langUrls(HttpServletRequest request) {
		Map<String, String> urls = new LinkedHashMap<>();
		LANGUAGES.forEach(lang -> urls.put(lang, switchTo(request.getQueryString(), lang)));
		return urls;
	}

	static String switchTo(String queryString, String lang) {
		return UriComponentsBuilder.newInstance()
			.query(queryString)
			.replaceQueryParam(LANG_PARAM, lang)
			.build()
			.toUriString();
	}

}
