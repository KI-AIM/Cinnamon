package de.kiaim.cinnamon.platform.processor;

import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.platform.model.dto.DataConfigurationEstimation;
import de.kiaim.cinnamon.platform.model.entity.FileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.cinnamon.platform.model.TransformationResult;
import de.kiaim.cinnamon.platform.model.file.FileType;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;

@Service
public class FhirProcessor implements DataProcessor {

    /**
     * {@inheritDoc}
     */
    @Override
    public FileType getSupportedDataType() {
        return FileType.FHIR;
    }

    @Override
    public int getNumberColumns(InputStream data, FileConfigurationEntity fileConfiguration) {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TransformationResult read(InputStream data, FileConfigurationEntity fileConfiguration,
                                     DataConfiguration configuration) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataConfigurationEstimation estimateDataConfiguration(InputStream data,
                                                                 FileConfigurationEntity fileConfiguration,
                                                                 final DatatypeEstimationAlgorithm algorithm) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(OutputStream outputStream, DataSet dataset) {
    }
}
