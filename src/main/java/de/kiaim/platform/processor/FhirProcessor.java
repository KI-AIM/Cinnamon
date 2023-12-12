package de.kiaim.platform.processor;

import de.kiaim.platform.model.FileConfiguration;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.model.TransformationResult;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class FhirProcessor implements DataProcessor{
    /**
     * {@inheritDoc}
     */
    @Override
    public TransformationResult read(InputStream data, FileConfiguration fileConfiguration,
                                     DataConfiguration configuration) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataConfiguration estimateDatatypes(InputStream data, FileConfiguration fileConfiguration) {
        return null;
    }
}
