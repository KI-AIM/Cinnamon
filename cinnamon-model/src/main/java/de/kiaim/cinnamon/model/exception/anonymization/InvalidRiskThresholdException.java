package de.kiaim.cinnamon.model.exception.anonymization;

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
