package de.kiaim.model.serialization.exception;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;

public class DataBuildingJsonException extends JsonProcessingException {
	public DataBuildingJsonException(String msg, JsonLocation loc, Throwable rootCause) {
		super(msg, loc, rootCause);
	}
}
