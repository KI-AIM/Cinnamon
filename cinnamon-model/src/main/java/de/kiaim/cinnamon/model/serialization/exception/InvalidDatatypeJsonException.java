package de.kiaim.cinnamon.model.serialization.exception;

import tools.jackson.core.JacksonException;
import tools.jackson.core.TokenStreamLocation;

public class InvalidDatatypeJsonException extends JacksonException {
	public InvalidDatatypeJsonException(String msg, TokenStreamLocation loc, Throwable rootCause) {
		super(msg, loc, rootCause);
	}
}
