package de.kiaim.model.exception.anonymization;

/**
 * Exception thrown if the attribute protection type is invalid.
 */
public class InvalidAttributeProtectionException extends FrontendConfigException {

  // Constructor with a custom message
  public InvalidAttributeProtectionException(String message) {
    super(message);
  }

  @Override
  public String getErrorType() {
    return "Invalid Attribute Protection";
  }
}
