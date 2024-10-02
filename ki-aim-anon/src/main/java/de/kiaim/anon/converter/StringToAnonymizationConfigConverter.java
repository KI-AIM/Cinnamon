package de.kiaim.anon.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.configuration.anonymization.AnonymizationConfig;
import de.kiaim.model.serialization.mapper.YamlMapper;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToAnonymizationConfigConverter implements Converter<String, AnonymizationConfig> {

    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;

    @Autowired
    public StringToAnonymizationConfigConverter(final ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.yamlMapper = YamlMapper.yamlMapper();
    }

    @Override
    @SneakyThrows
    public AnonymizationConfig convert(String value) {
        if (value.startsWith("{")) {
            return jsonMapper.readValue(value, AnonymizationConfig.class);
        } else {
            return yamlMapper.readValue(value, AnonymizationConfig.class);
        }
    }
}
