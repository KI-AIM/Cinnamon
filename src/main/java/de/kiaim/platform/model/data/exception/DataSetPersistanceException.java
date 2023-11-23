package de.kiaim.platform.model.data.exception;

public class DataSetPersistanceException extends Exception {
	public DataSetPersistanceException(final String message) {
		super(message);
	}
	public DataSetPersistanceException(final String message, final Exception cause) {
		super(message, cause);
	}
}
