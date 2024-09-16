package de.kiaim.anon.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.configuration.anonymization.AnonymizationConfig;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringAnonConfigConverter implements Converter<String, AnonymizationConfig> {
    private final ObjectMapper objectMapper;

    @Autowired
    public StringAnonConfigConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @SneakyThrows
    @Override
    public AnonymizationConfig convert(String value) {
        return objectMapper.readValue(value, AnonymizationConfig.class);
    }
}
