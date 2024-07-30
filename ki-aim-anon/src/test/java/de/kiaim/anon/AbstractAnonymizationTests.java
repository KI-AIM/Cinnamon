package de.kiaim.anon;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.anon.model.AnonymizationRequest;
import de.kiaim.anon.service.AnonymizationService;
import de.kiaim.model.configuration.anonymization.AnonConfigReader;
import de.kiaim.model.configuration.anonymization.AnonymizationConfig;
import de.kiaim.model.data.DataSet;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.ResourceUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

import static de.kiaim.anon.service.CompatibilityAssurance.isDataSetCompatible;
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

    protected MockWebServer mockWebServer;

    protected DataSet dataSet;
    protected AnonymizationConfig kiaimAnonConfig;
    protected String processId;
    protected AnonymizationRequest request;
    protected String mockUrl;

    @BeforeEach
    public void setUp() throws Exception {
        String datasetPath = "data/data.json-dataset-demo-data_DE 25k.json";
        String anonConfigPath = "data/data.csv-anon-configuration-demodata-v1.yml";

        dataSet = importDataset(datasetPath);
        kiaimAnonConfig = importAnonConfig(anonConfigPath);
        processId = "testProcess123";

        if (mockWebServer == null) {
            mockWebServer = new MockWebServer();
            mockWebServer.start();
        }
        mockUrl = mockWebServer.url("/test/callback").toString();
        request = new AnonymizationRequest(processId, dataSet, kiaimAnonConfig, mockUrl);

        // Check objects validity
        assert isDataSetCompatible(dataSet);
        // TODO : add check between data and anon config

    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
            mockWebServer = null;
        }
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
