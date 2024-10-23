package de.kiaim.platform.processor;

import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.platform.exception.BadColumnNameException;
import de.kiaim.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.platform.model.file.FileConfiguration;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.file.FileType;

import java.io.InputStream;

public interface DataProcessor {


	/**
	 * Returns the FileType this processor can handle.
	 * @return The FileType.
	 */
	FileType getSupportedDataType();

	/**
	 * Returns the number of columns in the given data.
	 * @param data The raw data InputStream
	 * @param fileConfiguration Configuration describing the format of the data.
	 * @return The number of columns in the data.
	 */
	int getNumberColumns(InputStream data, FileConfiguration fileConfiguration);

    /**
     * Receives data from frontend, converts it to
     * the corresponding filetype and performs the
     * correct transformation method to create a DataSet
     * and a TransformationResult with the transformation errors
     * @param data the raw data InputStream
     * @param fileConfiguration Configuration describing the format of the data.
     * @return TransformationResult
     */
    TransformationResult read(InputStream data, FileConfiguration fileConfiguration, DataConfiguration configuration)
		    throws BadColumnNameException;

    /**
     * Receives data from frontend, converts it to
     * the corresponding filetype and tries to estimate
     * the datatypes and datatype dependent configurations of each column.
     * @param data the raw data InputStream
     * @param fileConfiguration Configuration describing the format of the data.
     * @return DataConfiguration, only DataConfiguration populated
     */
    DataConfiguration estimateDataConfiguration(InputStream data, FileConfiguration fileConfiguration, DatatypeEstimationAlgorithm algorithm);

}
