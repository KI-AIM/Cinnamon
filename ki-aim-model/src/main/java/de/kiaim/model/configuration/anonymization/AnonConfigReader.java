package de.kiaim.model.configuration.anonymization;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Class to read yaml config files needed for anonymization. For test purpose only.
 */

@Slf4j
@Component
public class AnonConfigReader {

    private final ObjectMapper objectMapper;

    public AnonConfigReader() {
        this.objectMapper = new ObjectMapper(new YAMLFactory());
        this.objectMapper.findAndRegisterModules();
        this.objectMapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        this.objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public AnonymizationConfig readAnonymizationConfig(String pathToAnonymizationConfig) throws IOException {
        log.debug("Reading anonymization configuration from {}", pathToAnonymizationConfig);
        try (InputStream input = Files.newInputStream(Paths.get(pathToAnonymizationConfig))) {
            return objectMapper.readValue(input, AnonymizationConfig.class);
        } catch (IOException e) {
            log.error("Failed to read anonymization configuration from {}", pathToAnonymizationConfig, e);
            throw e;
        }
    }
}
