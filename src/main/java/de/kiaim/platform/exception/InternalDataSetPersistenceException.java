package de.kiaim.platform.exception;

public class InternalDataSetPersistenceException extends InternalException {
	public InternalDataSetPersistenceException(final String message) {
		super(message);
	}
	public InternalDataSetPersistenceException(final String message, final Exception cause) {
		super(message, cause);
	}
}
