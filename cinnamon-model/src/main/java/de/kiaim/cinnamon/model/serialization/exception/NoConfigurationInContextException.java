package de.kiaim.cinnamon.model.serialization.exception;

import tools.jackson.core.JacksonException;

public class NoConfigurationInContextException extends JacksonException {
	public NoConfigurationInContextException(String msg) {
		super(msg);
	}
}
