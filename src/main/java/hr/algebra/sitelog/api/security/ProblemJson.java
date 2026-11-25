package hr.algebra.sitelog.api.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes an RFC 9457 problem document from the security filter chain, where no controller
 * (and so no {@code @RestControllerAdvice}) is involved yet. Same members as the
 * {@code ApiExceptionHandler} bodies: type, title, status, detail, instance, code.
 */
final class ProblemJson {

	private ProblemJson() {
	}

	static void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail,
			String code) throws IOException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", "about:blank");
		body.put("title", status.getReasonPhrase());
		body.put("status", status.value());
		body.put("detail", detail);
		body.put("instance", request.getRequestURI());
		body.put("code", code);
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		JsonMapper.shared().writeValue(response.getOutputStream(), body);
	}

}
