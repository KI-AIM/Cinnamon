package de.kiaim.platform.helper;

import de.kiaim.platform.model.TransformationErrorType;
import de.kiaim.platform.model.data.configuration.DateTimeFormatConfiguration;
import de.kiaim.platform.model.data.exception.*;

public class ExceptionToTransformationErrorMapper {

    public TransformationErrorType mapException(Exception e) {
        if (e instanceof BooleanFormatException) {
            return TransformationErrorType.FORMAT_ERROR;
        } else if (e instanceof ConfigurationFormatException) {
            return TransformationErrorType.CONFIG_ERROR;
        } else if (e instanceof DateFormatException) {
            return TransformationErrorType.FORMAT_ERROR;
        } else if (e instanceof DateTimeFormatException) {
            return TransformationErrorType.FORMAT_ERROR;
        } else if (e instanceof FloatFormatException) {
            return TransformationErrorType.FORMAT_ERROR;
        } else if (e instanceof IntFormatException) {
            return TransformationErrorType.FORMAT_ERROR;
        } else if (e instanceof StringPatternException) {
            return TransformationErrorType.FORMAT_ERROR;
        } else if (e instanceof MissingValueException) {
            return TransformationErrorType.MISSING_VALUE;
        } else {
            return TransformationErrorType.OTHER;
        }
    }

}
