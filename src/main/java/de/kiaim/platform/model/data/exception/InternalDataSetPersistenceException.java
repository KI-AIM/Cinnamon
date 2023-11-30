package de.kiaim.platform.model.data.exception;

public class InternalDataSetPersistenceException extends Exception {
	public InternalDataSetPersistenceException(final String message) {
		super(message);
	}
	public InternalDataSetPersistenceException(final String message, final Exception cause) {
		super(message, cause);
	}
}
