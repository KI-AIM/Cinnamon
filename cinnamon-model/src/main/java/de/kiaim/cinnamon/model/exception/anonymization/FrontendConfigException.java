package de.kiaim.cinnamon.model.exception.anonymization;

/**
 * Base exception for all exceptions that occur during handling the Frontend Anonymization Config.
 */
public abstract class FrontendConfigException extends Exception {
    public FrontendConfigException(String message) {
        super(message);
    }

    // Constructor with a custom message and cause
    public FrontendConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    // Abstract method that can be overridden to get specific error types, if needed
    public abstract String getErrorType();
}
