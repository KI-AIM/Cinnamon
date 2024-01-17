package de.kiaim.platform.json;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;

public class InvalidDatatypeJsonException extends JsonProcessingException {
	protected InvalidDatatypeJsonException(String msg, JsonLocation loc, Throwable rootCause) {
		super(msg, loc, rootCause);
	}
}
