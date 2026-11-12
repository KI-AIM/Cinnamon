package de.kiaim.cinnamon.model.configuration.anonymization.frontend;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
@Component
public class FrontendAnonConfigReader {
    private final ObjectMapper yamlMapper;

    public FrontendAnonConfigReader() {
        this.yamlMapper = YAMLMapper.builder()
                                    .findAndAddModules()
                                    .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                    .build();
    }

    public FrontendAnonConfig readFrontendAnonConfig(String pathToFrontendConfig) throws IOException {
        log.debug("Reading frontend anonymization configuration from {}", pathToFrontendConfig);
        try (InputStream input = Files.newInputStream(Paths.get(pathToFrontendConfig))) {
            return yamlMapper.readValue(input, FrontendAnonConfig.class);
        } catch (IOException | JacksonException e) {
            log.error("Failed to read frontend anonymization configuration from {}", pathToFrontendConfig, e);
            throw e;
        }
    }
}
