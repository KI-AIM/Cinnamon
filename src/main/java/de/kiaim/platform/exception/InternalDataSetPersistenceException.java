package de.kiaim.platform.exception;

/**
 * Exceptions that occur when working with the database.
 */
public class InternalDataSetPersistenceException extends InternalException {

	/**
	 * Exception code for errors during the table creation.
	 */
	public static final String TABLE_CREATE = "1";

	/**
	 * Exception code for errors when checking for the existing of a table.
	 */
	public static final String TABLE_CHECk = "2";

	/**
	 * Exception code for errors during the persistence of data sets.
	 */
	public static final String DATA_SET_STORE = "3";

	/**
	 * Exception code for errors during the deletion of data sets and the corresponding table.
	 */
	public static final String DATA_SET_DELETE = "4";

	/**
	 * Exception code for errors during the export of data sets.
	 */
	public static final String DATA_SET_EXPORT = "5";

	/**
	 * Exception code for errors when storing data because of invalid data types.
	 */
	public static final String DATA_TYPE_STORE = "6";

	/**
	 * Exception code for errors when exporting data because of invalid data types.
	 */
	public static final String DATA_TYPE_EXPORT = "7";

	/**
	 * Exception code for errors during the export because of a failed value conversion.
	 */
	public static final String VALUE_CONVERSION = "8";

	public InternalDataSetPersistenceException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	public InternalDataSetPersistenceException(final String exceptionCode, final String message,
	                                           final Exception cause) {
		super(exceptionCode, message, cause);
	}

	@Override
	protected String getExceptionClassCode() {
		return DATA_SET_PERSISTENCE;
	}
}
