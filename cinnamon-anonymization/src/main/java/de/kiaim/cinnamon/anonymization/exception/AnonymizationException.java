package de.kiaim.cinnamon.anonymization.exception;

import lombok.Getter;

@Getter
public class AnonymizationException extends RuntimeException {
  private final String errorCode;

  public AnonymizationException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }
}
