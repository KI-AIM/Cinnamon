package de.kiaim.model.serialization;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;

public class DataBuildingJsonException extends JsonProcessingException {
	protected DataBuildingJsonException(String msg, JsonLocation loc, Throwable rootCause) {
		super(msg, loc, rootCause);
	}
}
