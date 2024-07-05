package de.kiaim.anon;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.anon.service.AnonymizationService;
import de.kiaim.model.configuration.anonymization.AnonConfigReader;
import de.kiaim.model.configuration.anonymization.AnonymizationConfig;
import de.kiaim.model.data.DataSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.ResourceUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Reusable class to set up tests for anonymization classes.
 */
@SpringBootTest
public class AbstractAnonymizationTests {
    @Autowired
    protected AnonymizationService anonymizationService;

    @Autowired
    protected AnonConfigReader anonConfigReader;

    @Autowired
    protected ObjectMapper objectMapper;

    protected DataSet dataSet;
    protected AnonymizationConfig kiaimAnonConfig;

    @BeforeEach
    public void setUp() throws Exception {
        String datasetPath = "data/data.json-dataset-demo-data_DE 25k.json";
        String anonConfigPath = "data/data.csv-anon-configuration-demodata-v1.yml";

        dataSet = importDataset(datasetPath);
        kiaimAnonConfig = importAnonConfig(anonConfigPath);
    }

    public DataSet importDataset(String datasetPath) throws IOException {
        String dataSetJson = new String(Files.readAllBytes(Paths.get("data/data.json-dataset-demo-data_DE 25k.json")));
        return objectMapper.readValue(dataSetJson, DataSet.class);
    }

    public AnonymizationConfig importAnonConfig(String anonConfigPath) throws IOException {
        File file = ResourceUtils.getFile(anonConfigPath);
        return anonConfigReader.readAnonymizationConfig(file.getAbsolutePath());
    }

    @Test
    public void testProcessAnonymization() throws Exception {
        assertNotNull(dataSet);
        assertNotNull(kiaimAnonConfig);
//        System.out.println(kiaimAnonConfig);
    }
}
