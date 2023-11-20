package de.kiaim.platform.processor;

import de.kiaim.platform.model.DataConfiguration;
import de.kiaim.platform.model.TransformationResult;

import java.io.InputStream;
import java.util.Base64;

public class XlsxProcessor implements DataProcessor{
    @Override
    public TransformationResult read(InputStream data) {
        return null;
    }

    @Override
    public DataConfiguration estimateDatatypes(InputStream data) {
        return null;
    }
}
