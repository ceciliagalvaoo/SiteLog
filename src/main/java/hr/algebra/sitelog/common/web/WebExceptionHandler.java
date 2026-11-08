package hr.algebra.sitelog.common.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.DomainException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.common.exception.LogLockedException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Turns domain and persistence exceptions thrown on SCREENS into pages or flash messages
 * (the REST API gets its own {@code @RestControllerAdvice} with {@code ProblemDetail} in M3).
 *
 * <ul>
 * <li>Stale version / concurrent commit -> <b>409</b> page {@code error/409} with the flash
 * "This daily log was changed by someone else. Reload and try again." and a reload link.</li>
 * <li>Locked (signed-off) log -> redirect to the log detail with an error flash (PRG).</li>
 * <li>Invalid workflow step, refused delete -> redirect back with an error flash.</li>
 * <li>Not found -> <b>404</b> page {@code error/404}.</li>
 * <li>Database rule caught only by a constraint or the lock trigger -> <b>409</b> page.</li>
 * </ul>
 * Error pages never show stack traces ({@code server.error.include-stacktrace=never}).
 */
@ControllerAdvice(annotations = Controller.class)
public class WebExceptionHandler {

	static final String CONFLICT_VIEW = "error/409";

	static final String NOT_FOUND_VIEW = "error/404";

	static final String CONFLICT_KEY = "dailyLog.flash.conflict";

	static final String LOCKED_KEY = "dailyLog.flash.locked";

	static final String DATA_CONFLICT_KEY = "error.conflict.data";

	/** Message of the V3 lock trigger ({@code RAISE EXCEPTION ... MESSAGE = 'daily_log_locked'}). */
	static final String LOCK_TRIGGER_MESSAGE = "daily_log_locked";

	private static final Logger LOG = LoggerFactory.getLogger(WebExceptionHandler.class);

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ModelAndView optimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
		String reloadUrl = DailyLog.class.getName().equals(ex.getPersistentClassName()) && ex.getIdentifier() != null
				? "/daily-logs/" + ex.getIdentifier()
				: refererOr(request, "/sites");
		LOG.info("Optimistic lock conflict on {} {}", ex.getPersistentClassName(), ex.getIdentifier());
		return conflictPage(CONFLICT_KEY, reloadUrl);
	}

	@ExceptionHandler(LogLockedException.class)
	public String logLocked(LogLockedException ex, RedirectAttributes redirect) {
		Flash.error(redirect, ex.getMessageKey());
		return "redirect:/daily-logs/" + ex.getDailyLogId();
	}

	@ExceptionHandler({ InvalidTransitionException.class, DeletionNotAllowedException.class })
	public String businessRule(DomainException ex, HttpServletRequest request,
			RedirectAttributes redirect) {
		Flash.error(redirect, ex.getMessageKey(), ex.getArgs());
		return SafeRedirects.back(request, "/sites");
	}

	@ExceptionHandler(NotFoundException.class)
	public ModelAndView notFound(NotFoundException ex) {
		ModelAndView page = new ModelAndView(NOT_FOUND_VIEW);
		page.setStatus(HttpStatus.NOT_FOUND);
		page.addObject("errorMessage", ex.getMessageKey());
		return page;
	}

	/**
	 * Second line of defence: a rule the service did not catch but the database did (unique or
	 * check constraint, or the lock trigger on a signed-off log). Anything else is re-thrown and
	 * ends on the generic error page.
	 */
	@ExceptionHandler(DataAccessException.class)
	public ModelAndView dataAccess(DataAccessException ex, HttpServletRequest request) throws DataAccessException {
		Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
		String message = root.getMessage() == null ? "" : root.getMessage();
		if (message.contains(LOCK_TRIGGER_MESSAGE)) {
			LOG.warn("Lock trigger rejected a write the service did not stop: {}", message);
			return conflictPage(LOCKED_KEY, refererOr(request, "/sites"));
		}
		if (ex instanceof DataIntegrityViolationException) {
			LOG.warn("Constraint violation: {}", message);
			return conflictPage(DATA_CONFLICT_KEY, refererOr(request, "/sites"));
		}
		throw ex;
	}

	private static ModelAndView conflictPage(String messageKey, String reloadUrl) {
		ModelAndView page = new ModelAndView(CONFLICT_VIEW);
		page.setStatus(HttpStatus.CONFLICT);
		// rendered by the layout's flash stack like a flash message after a redirect
		page.addObject(Flash.ERROR, messageKey);
		page.addObject("reloadUrl", reloadUrl);
		return page;
	}

	private static String refererOr(HttpServletRequest request, String fallback) {
		return SafeRedirects.back(request, fallback).substring("redirect:".length());
	}

}
