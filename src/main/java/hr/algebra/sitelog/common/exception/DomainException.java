package hr.algebra.sitelog.common.exception;

/**
 * Base class of the business exceptions thrown by services. Each one carries a message key
 * (resolved from {@code messages.properties} by the web layer, so the text follows the UI
 * language) and optional arguments for the placeholders of that message.
 *
 * <p>Services never build user-facing text; controllers and exception handlers turn the key
 * into a flash message, a field error or an error page.
 */
public abstract class DomainException extends RuntimeException {

	private final String messageKey;

	private final transient Object[] args;

	protected DomainException(String messageKey, String detail, Object... args) {
		super(detail);
		this.messageKey = messageKey;
		this.args = args;
	}

	public String getMessageKey() {
		return messageKey;
	}

	public Object[] getArgs() {
		return args.clone();
	}

}
