package hr.algebra.sitelog.common.exception;

/** The requested record does not exist (HTTP 404). */
public class NotFoundException extends DomainException {

	public NotFoundException(Class<?> type, Object id) {
		super("error.notFound", type.getSimpleName() + " " + id + " not found");
	}

}
