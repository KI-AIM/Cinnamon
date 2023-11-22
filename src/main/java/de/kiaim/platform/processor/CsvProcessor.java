package de.kiaim.platform.processor;

import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.model.TransformationResult;

import java.io.InputStream;

public class CsvProcessor extends CommonDataProcessor{
    @Override
    public TransformationResult read(InputStream data) {
        return null;
    }

    @Override
    public DataConfiguration estimateDatatypes(InputStream data) {
        return null;
    }

}
