package de.kiaim.cinnamon.model.serialization.exception;

import tools.jackson.core.JacksonException;

public class DataFormatException extends JacksonException {
	public DataFormatException(String msg, Throwable rootCause) {
		super(msg, rootCause);
	}
}
