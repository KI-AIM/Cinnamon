package de.kiaim.model.exception.anonymization;

/**
 * Exception thrown when the generalization setting is not valid.
 */
public class InvalidGeneralizationSettingException extends FrontendConfigException {
    public InvalidGeneralizationSettingException(String message) {
        super(message);
    }

    @Override
    public String getErrorType() {
        return "Invalid Generalization Setting";
    }
}