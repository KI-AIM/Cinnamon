package de.kiaim.cinnamon.platform.processor;

import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.platform.exception.BadDatasetException;
import de.kiaim.cinnamon.platform.exception.BadFileException;
import de.kiaim.cinnamon.platform.exception.InternalIOException;
import de.kiaim.cinnamon.platform.model.dto.DataConfigurationEstimation;
import de.kiaim.cinnamon.platform.model.dto.FileConfigurationEstimation;
import de.kiaim.cinnamon.platform.model.entity.FileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.cinnamon.platform.model.TransformationResult;
import de.kiaim.cinnamon.platform.model.file.FileType;

import java.io.InputStream;
import java.io.OutputStream;

public interface DataProcessor {


	/**
	 * Returns the FileType this processor can handle.
	 * @return The FileType.
	 */
	FileType getSupportedDataType();

	/**
	 * Estimates the file configuration based on the file's content.
	 *
	 * @param data The file content
	 * @return The estimated file configuration.
	 * @throws BadFileException    If the data format cannot be processed.
	 * @throws InternalIOException If reading the data failed.
	 */
	FileConfigurationEstimation estimateFileConfiguration(InputStream data)
			throws InternalIOException, BadFileException;

	/**
	 * Returns the number of columns in the given data.
	 * @param data The raw data InputStream
	 * @param fileConfiguration Configuration describing the format of the data.
	 * @return The number of columns in the data.
	 * @throws InternalIOException If reading the data failed.
	 */
	int getNumberColumns(InputStream data, FileConfigurationEntity fileConfiguration) throws InternalIOException;

	/**
	 * Receives data from the frontend, converts it to
	 * the corresponding filetype and performs the
	 * correct transformation method to create a DataSet
	 * and a TransformationResult with the transformation errors
	 *
	 * @param data              the raw data InputStream
	 * @param fileConfiguration Configuration describing the format of the data.
	 * @return TransformationResult
	 * @throws BadDatasetException If the row has too few or too many values.
	 * @throws InternalIOException If reading the data failed.
	 */
	TransformationResult read(InputStream data, FileConfigurationEntity fileConfiguration,
	                          DataConfiguration configuration) throws BadDatasetException, InternalIOException ;

    /**
     * Receives data from frontend, converts it to
     * the corresponding filetype and tries to estimate
     * the datatypes and datatype dependent configurations of each column.
     * @param data the raw data InputStream
     * @param fileConfiguration Configuration describing the format of the data.
     * @return DataConfigurationEstimation, only DataConfiguration populated
     * @throws InternalIOException If reading the data failed.
     */
    DataConfigurationEstimation estimateDataConfiguration(InputStream data, FileConfigurationEntity fileConfiguration,
                                                          DatatypeEstimationAlgorithm algorithm) throws InternalIOException;

	/**
	 * Writes the data to the output stream.
	 *
	 * @param outputStream The output stream to write to.
	 * @param dataset      The dataset to write.
	 * @throws InternalIOException If reading the data failed.
	 */
	void write(OutputStream outputStream, DataSet dataset) throws InternalIOException;

}
