package de.kiaim.model.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;

public class NoConfigurationInContextException extends JsonProcessingException {
	protected NoConfigurationInContextException(String msg) {
		super(msg);
	}
}
