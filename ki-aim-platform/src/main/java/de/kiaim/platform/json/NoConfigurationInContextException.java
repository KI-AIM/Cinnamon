package de.kiaim.platform.json;

import com.fasterxml.jackson.core.JsonProcessingException;

public class NoConfigurationInContextException extends JsonProcessingException {
	protected NoConfigurationInContextException(String msg) {
		super(msg);
	}
}
