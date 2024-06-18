package de.kiaim.platform.json;

import com.fasterxml.jackson.core.JsonProcessingException;

public class DataFormatException extends JsonProcessingException {
	protected DataFormatException(String msg, Throwable rootCause) {
		super(msg, rootCause);
	}
}
