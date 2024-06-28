package de.kiaim.anon.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.configuration.anonymization.DatasetAnonymizationConfig;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringAnonConfigConverter implements Converter<String, DatasetAnonymizationConfig> {
    private final ObjectMapper objectMapper;

    @Autowired
    public StringAnonConfigConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @SneakyThrows
    @Override
    public DatasetAnonymizationConfig convert(String value) {
        return objectMapper.readValue(value, DatasetAnonymizationConfig.class);
    }
}
