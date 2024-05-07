package de.kiaim.platform.processor;

import de.kiaim.platform.model.file.FileConfiguration;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.file.FileType;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class XlsxProcessor implements DataProcessor {

    /**
     * {@inheritDoc}
     */
    @Override
    public FileType getSupportedDataType() {
        return FileType.XLSX;
    }

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
