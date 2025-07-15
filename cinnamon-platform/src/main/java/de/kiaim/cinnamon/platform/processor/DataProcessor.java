package de.kiaim.cinnamon.platform.processor;

import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.platform.exception.InternalIOException;
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
	 * Returns the number of columns in the given data.
	 * @param data The raw data InputStream
	 * @param fileConfiguration Configuration describing the format of the data.
	 * @return The number of columns in the data.
	 */
	int getNumberColumns(InputStream data, FileConfigurationEntity fileConfiguration);

    /**
     * Receives data from frontend, converts it to
     * the corresponding filetype and performs the
     * correct transformation method to create a DataSet
     * and a TransformationResult with the transformation errors
     * @param data the raw data InputStream
     * @param fileConfiguration Configuration describing the format of the data.
     * @return TransformationResult
     */
    TransformationResult read(InputStream data, FileConfigurationEntity fileConfiguration, DataConfiguration configuration);

    /**
     * Receives data from frontend, converts it to
     * the corresponding filetype and tries to estimate
     * the datatypes and datatype dependent configurations of each column.
     * @param data the raw data InputStream
     * @param fileConfiguration Configuration describing the format of the data.
     * @return DataConfiguration, only DataConfiguration populated
     */
    DataConfiguration estimateDataConfiguration(InputStream data, FileConfigurationEntity fileConfiguration, DatatypeEstimationAlgorithm algorithm);

	/**
	 * Writes the data to the output stream.
	 *
	 * @param outputStream The output stream to write to.
	 * @param dataset      The dataset to write.
	 */
	void write(OutputStream outputStream, DataSet dataset) throws InternalIOException;

}
