package hr.algebra.sitelog.api.common;

/** A paging or sorting parameter the collection does not support (HTTP 400). */
public class InvalidPageRequestException extends RuntimeException {

	public InvalidPageRequestException(String message) {
		super(message);
	}

}
