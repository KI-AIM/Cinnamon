package de.kiaim.cinnamon.platform.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Exception for issues regarding the email templates of the application.
 *
 * @author Daniel Preciado-Marquez
 */
public class BadEmailTemplateException extends BadRequestException {

	/**
	 * Exception code for a template that does not exist.
	 */
	public static final String NOT_FOUND = "1";

	/**
	 * Exception code for a name that is already used by another template.
	 */
	public static final String NAME_EXISTS = "2";

	private final HttpStatusCode status;

	private BadEmailTemplateException(final String exceptionCode, final String message, final HttpStatusCode status) {
		super(exceptionCode, message);
		this.status = status;
	}

	/**
	 * Creates an exception for a template that does not exist.
	 *
	 * @param id The ID of the requested template.
	 * @return The exception.
	 */
	public static BadEmailTemplateException notFound(final Long id) {
		return new BadEmailTemplateException(NOT_FOUND, "The email template with the ID '" + id + "' does not exist!",
		                                     HttpStatus.NOT_FOUND);
	}

	/**
	 * Creates an exception for a name that is already used by another template.
	 * The name is unique because it is used to reference a template.
	 *
	 * @param name The requested name.
	 * @return The exception.
	 */
	public static BadEmailTemplateException nameExists(final String name) {
		return new BadEmailTemplateException(NAME_EXISTS,
		                                     "An email template with the name '" + name + "' already exists!",
		                                     HttpStatus.CONFLICT);
	}

	@Override
	public HttpStatusCode getStatus() {
		return status;
	}

	@Override
	protected String getExceptionClassCode() {
		return EMAIL_TEMPLATE;
	}
}
