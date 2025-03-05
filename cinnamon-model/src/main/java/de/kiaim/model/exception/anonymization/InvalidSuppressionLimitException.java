package de.kiaim.model.exception.anonymization;

/**
 * Exception thrown when the suppression limit is out of bounds.
 */
public class InvalidSuppressionLimitException extends FrontendConfigException {
    public InvalidSuppressionLimitException(String message) {
        super(message);
    }

    @Override
    public String getErrorType() {
        return "Invalid Suppression Limit";
    }
}
