package de.kiaim.model.exception.anonymization;

import de.kiaim.model.enumeration.TransformationErrorType;
import de.kiaim.model.exception.DataBuildingException;

/**
 * Exception thrown if the risk threshold value is invalid based on the type.
 */
public class InvalidRiskThresholdException extends FrontendConfigException {

    // Constructor with a custom message
    public InvalidRiskThresholdException(String message) {
        super(message);
    }

    @Override
    public String getErrorType() {
        return "Invalid Risk Threshold";
    }
}
