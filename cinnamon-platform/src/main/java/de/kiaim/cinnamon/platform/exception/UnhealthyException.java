package de.kiaim.cinnamon.platform.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

/**
 * Exception for health checks that return an HTTP error code.
 *
 * @author Daniel Preciado-Marquez
 */
public class UnhealthyException extends RuntimeException {

	/**
	 * The status resulting from the health check.
	 */
	@Getter
	private final String status;

	/**
	 * The returned HTTP status code.
	 */
	@Getter
	private final HttpStatusCode statusCode;

	public UnhealthyException(final String status, final HttpStatusCode statusCode) {
		this.status = status;
		this.statusCode = statusCode;
	}
}
