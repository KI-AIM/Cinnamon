package de.kiaim.platform.model.data.exception;

import de.kiaim.platform.model.TransformationErrorType;

/**
 * Exception thrown, if the configuration is not valid
 */
public class ConfigurationFormatException extends DataBuildingException {

	@Override
	public TransformationErrorType getTransformationErrorType() {
		return TransformationErrorType.CONFIG_ERROR;
	}
}
