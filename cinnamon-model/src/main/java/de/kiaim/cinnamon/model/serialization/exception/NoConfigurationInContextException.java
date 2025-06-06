package de.kiaim.cinnamon.model.serialization.exception;

import com.fasterxml.jackson.core.JsonProcessingException;

public class NoConfigurationInContextException extends JsonProcessingException {
	public NoConfigurationInContextException(String msg) {
		super(msg);
	}
}
