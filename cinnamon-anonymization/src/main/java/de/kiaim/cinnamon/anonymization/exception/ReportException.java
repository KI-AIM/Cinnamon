package de.kiaim.cinnamon.anonymization.exception;

/**
 * Exception occurring during the creation of the report content.
 *
 * @author Daniel Preciado-Marquez
 */
public class ReportException extends AnonymizationException {

	/**
	 * Creates a new report exception.
	 *
	 * @param message Human-readable error message.
	 * @param cause   Underlying cause for the exception.
	 */
	public ReportException(final String message, final Throwable cause) {
		super("ANON_3_1_1", message, cause);
	}
}
