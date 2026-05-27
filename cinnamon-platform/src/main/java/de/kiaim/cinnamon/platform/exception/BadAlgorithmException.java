package de.kiaim.cinnamon.platform.exception;

/**
 * Exception for requesting an algorithm that is not available in the external module.
 *
 * @author Daniel Preciado-Marquez
 */
public class BadAlgorithmException extends BadRequestException {

	/**
	 * Exception code for requesting an algorithm that is not available in the external module.
	 */
	public final static String ALGORITHM_NOT_AVAILABLE = "1";

	/**
	 * Exception code for providing a configuration without selecting an algorithm.
	 */
	public final static String ALGORITHM_NOT_SELECTED = "2";

	public BadAlgorithmException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return ALGORITHM;
	}
}
