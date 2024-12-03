package de.kiaim.platform.processor;

import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.platform.model.entity.FileConfigurationEntity;
import de.kiaim.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.file.FileType;
import org.springframework.stereotype.Service;

import java.io.InputStream;

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
    public DataConfiguration estimateDataConfiguration(InputStream data, FileConfigurationEntity fileConfiguration,
                                                       final DatatypeEstimationAlgorithm algorithm) {
        return null;
    }
}
