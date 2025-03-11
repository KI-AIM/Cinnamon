package de.kiaim.cinnamon.anonymization.exception;

public class UnexpectedAnonymizationException extends AnonymizationException {
  public UnexpectedAnonymizationException(Throwable cause) {
    super("ANON_UNKNOWN", "An unexpected error occurred: " + cause.getMessage());
  }}
