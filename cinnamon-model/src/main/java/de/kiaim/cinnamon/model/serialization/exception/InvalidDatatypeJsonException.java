package de.kiaim.cinnamon.model.serialization.exception;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;

public class InvalidDatatypeJsonException extends JsonProcessingException {
	public InvalidDatatypeJsonException(String msg, JsonLocation loc, Throwable rootCause) {
		super(msg, loc, rootCause);
	}
}
