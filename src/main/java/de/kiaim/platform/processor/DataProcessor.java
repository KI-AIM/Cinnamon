package de.kiaim.platform.processor;

import de.kiaim.platform.model.DataConfiguration;
import de.kiaim.platform.model.TransformationResult;

import java.util.Base64;

public interface DataProcessor {

    /**
     * Receives byte data from frontend, converts it to
     * the corresponding filetype and performs the
     * correct transformation method to create a DataSet
     * and a TransformationResult with the transformation errors
     * @param data the raw data String base64 encoded
     * @return TransformationResult
     */
    TransformationResult read(Base64 data);

    /**
     * Receives byte data from frontend, converts it to
     * the corresponding filetype and tries to estimate
     * the datatypes of each column. The result will be
     * returned in a partial DataConfiguration object
     * where only the DataTypes are specified
     * @param data the raw data String base64 encoded
     * @return DataConfiguration, only DataConfiguration populated
     */
    DataConfiguration estimateDatatypes(Base64 data);

}
