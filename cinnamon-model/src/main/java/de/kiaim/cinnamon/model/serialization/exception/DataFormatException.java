package de.kiaim.cinnamon.model.serialization.exception;

import com.fasterxml.jackson.core.JsonProcessingException;

public class DataFormatException extends JsonProcessingException {
	public DataFormatException(String msg, Throwable rootCause) {
		super(msg, rootCause);
	}
}
