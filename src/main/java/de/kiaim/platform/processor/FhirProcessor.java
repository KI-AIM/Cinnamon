package de.kiaim.platform.processor;

import de.kiaim.platform.model.DataConfiguration;
import de.kiaim.platform.model.TransformationResult;

import java.util.Base64;

public class FhirProcessor implements DataProcessor{
    @Override
    public TransformationResult read(Base64 data) {
        return null;
    }

    @Override
    public DataConfiguration estimateDatatypes(Base64 data) {
        return null;
    }
}
