package de.kiaim.cinnamon.anonymization;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.anonymization.model.AnonymizationRequest;
import de.kiaim.cinnamon.anonymization.service.AnonymizationService;
import de.kiaim.cinnamon.model.configuration.anonymization.frontend.FrontendAnonConfigWrapper;
import de.kiaim.cinnamon.model.configuration.anonymization.frontend.FrontendAnonConfigWrapperReader;
import de.kiaim.cinnamon.model.data.DataSet;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Reusable class to set up tests for anonymization classes.
 */
@SpringBootTest
public class AbstractAnonymizationTests {
    @Autowired
    protected AnonymizationService anonymizationService;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected FrontendAnonConfigWrapperReader frontendAnonConfigWrapperReader;

    protected MockWebServer mockWebServer;

    protected DataSet dataSet;
    protected FrontendAnonConfigWrapper frontendAnonConfig;
    protected DataSet heartDataset;
    protected FrontendAnonConfigWrapper heartFrontendAnonConfig;
    protected String processId;
    protected AnonymizationRequest request;
    protected String mockUrl;

    @BeforeEach
    public void setUp() throws Exception {
        objectMapper.findAndRegisterModules();

        String datasetPath = "data/oncology/data.json-dataset-demo-data_DE 25k.json";
        String frontendAnonConfigPath = "data/oncology/data.example-new-anon-config-demodata.yml";

        String heartDatasetPath = "data/data.json-dataset-heart-failure.json";
        String heartFrontendAnonConfigPath = "data/data.heart-failure-anon-config.yml";
        dataSet = importDataset(datasetPath);

        heartDataset = importDataset(heartDatasetPath);
        heartFrontendAnonConfig = importFrontendAnonConfig(heartFrontendAnonConfigPath);

        frontendAnonConfig = importFrontendAnonConfig(frontendAnonConfigPath);
        processId = "testProcess123";

//
//        System.out.println("FrontendAnonConfigWrapper");
//        System.out.println(heartFrontendAnonConfig.toString());

        if (mockWebServer == null) {
            mockWebServer = new MockWebServer();
            mockWebServer.start();
        }
        mockUrl = mockWebServer.url("/test/callback").toString();
        request = new AnonymizationRequest(processId, dataSet, frontendAnonConfig.getAnonymization(), mockUrl);

        // Check objects validity
//        assert isDataSetCompatible(dataSet);
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
        String dataSetJson = new String(Files.readAllBytes(Paths.get(datasetPath)));
        return objectMapper.readValue(dataSetJson, DataSet.class);
    }

    public FrontendAnonConfigWrapper importFrontendAnonConfig(String frontendAnonConfigPath) throws IOException {
        File file = ResourceUtils.getFile(frontendAnonConfigPath);
        return frontendAnonConfigWrapperReader.readFrontendAnonConfigWrapper(file.getAbsolutePath());
    }

    @Test
    public void testProcessAnonymization() throws Exception {
        assertNotNull(dataSet);
//        System.out.println(kiaimAnonConfig);
    }
}
