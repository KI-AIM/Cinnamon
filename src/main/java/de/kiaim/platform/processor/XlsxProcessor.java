package de.kiaim.platform.processor;

import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.model.TransformationResult;

import java.io.InputStream;

public class XlsxProcessor implements DataProcessor{
    /**
     * {@inheritDoc}
     */
    @Override
    public TransformationResult read(InputStream data, DataConfiguration configuration) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataConfiguration estimateDatatypes(InputStream data) {
        return null;
    }
}
