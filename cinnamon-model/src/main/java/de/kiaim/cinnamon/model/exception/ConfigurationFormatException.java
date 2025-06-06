package de.kiaim.cinnamon.model.exception;

import de.kiaim.cinnamon.model.enumeration.TransformationErrorType;

/**
 * Exception throw if the configuration is not valid.
 */
public class ConfigurationFormatException extends DataBuildingException {

	@Override
	public TransformationErrorType getTransformationErrorType() {
		return TransformationErrorType.CONFIG_ERROR;
	}
}
