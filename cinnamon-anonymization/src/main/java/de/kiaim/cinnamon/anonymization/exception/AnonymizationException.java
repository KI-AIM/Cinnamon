package de.kiaim.cinnamon.anonymization.exception;

import lombok.Getter;

@Getter
public class AnonymizationException extends RuntimeException {
  private final String errorCode;

  public AnonymizationException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

	/**
	 * Creates a new anonymization exception.
	 *
	 * @param errorCode Code identifying the error.
	 * @param message   Human-readable error message.
	 * @param cause     Underlying cause for the exception.
	 */
	public AnonymizationException(final String errorCode, final String message, final Throwable cause) {
		super(message);
		this.errorCode = errorCode;
	}
}
