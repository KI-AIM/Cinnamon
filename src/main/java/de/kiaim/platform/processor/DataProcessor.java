package de.kiaim.platform.processor;

import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.model.TransformationResult;

import java.io.InputStream;

public interface DataProcessor {

    /**
     * Receives data from frontend, converts it to
     * the corresponding filetype and performs the
     * correct transformation method to create a DataSet
     * and a TransformationResult with the transformation errors
     * @param data the raw data InputStream
     * @return TransformationResult
     */
    TransformationResult read(InputStream data);

    /**
     * Receives data from frontend, converts it to
     * the corresponding filetype and tries to estimate
     * the datatypes of each column. The result will be
     * returned in a partial DataConfiguration object
     * where only the DataTypes are specified
     * @param data the raw data InputStream
     * @return DataConfiguration, only DataConfiguration populated
     */
    DataConfiguration estimateDatatypes(InputStream data);

}
