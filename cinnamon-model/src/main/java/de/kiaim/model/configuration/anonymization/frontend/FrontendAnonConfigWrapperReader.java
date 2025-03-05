package de.kiaim.model.configuration.anonymization.frontend;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
@Component
public class FrontendAnonConfigWrapperReader {

    private final ObjectMapper yamlMapper;

    public FrontendAnonConfigWrapperReader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.findAndRegisterModules();
        this.yamlMapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        this.yamlMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public FrontendAnonConfigWrapper readFrontendAnonConfigWrapper(String pathToFrontendConfig) throws IOException {
        log.debug("Reading frontend anonymization configuration from {}", pathToFrontendConfig);
        try (InputStream input = Files.newInputStream(Paths.get(pathToFrontendConfig))) {
            return yamlMapper.readValue(input, FrontendAnonConfigWrapper.class);
        } catch (IOException e) {
            log.error("Failed to read frontend anonymization configuration from {}", pathToFrontendConfig, e);
            throw e;
        }
    }
}
