package de.kiaim.model.exception.anonymization;

/**
 * Exception thrown when an attribute configuration is invalid.
 */
public class InvalidAttributeConfigException extends FrontendConfigException {
    public InvalidAttributeConfigException(String message) {
        super(message);
    }

    @Override
    public String getErrorType() {
        return "Invalid Attribute Configuration";
    }
}

