package de.kiaim.anon.service;

import de.kiaim.anon.AbstractAnonymizationTests;
import de.kiaim.anon.config.AnonymizationConfig;
import de.kiaim.anon.converter.KiaimAnonConfigConverter;
import de.kiaim.anon.model.AnonymizationRequest;
import de.kiaim.model.configuration.data.ColumnConfiguration;
import de.kiaim.model.data.DataSet;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import smile.data.Dataset;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static util.GenerateTestDatasets.generateDataSetWithConfig;

@SpringBootTest
public class AnonymizationServiceTest extends AbstractAnonymizationTests {

    @Autowired
    private AnonymizationService anonymizationService;

    @Autowired
    private KiaimAnonConfigConverter datasetAnonConfigConverter;

    @BeforeEach
    void setUpService() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    public void testAnonymizationService() throws Exception {

        Future<DataSet> future = anonymizationService.anonymizeData(dataSet, kiaimAnonConfig, "processIdTest");

        if (!future.isDone()) {
            for (int i = 0; i<30; i++) {
                Thread.sleep(100);
            }
        }

        try {
            DataSet anonymizedDataset = future.get();
            assertNotNull(anonymizedDataset);
            System.out.println(anonymizedDataset.getDataRows());
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    public void testAnonymizeDataWithCallback() throws Exception {
        mockWebServer.enqueue(new MockResponse().setBody("ok").setResponseCode(200));

        anonymizationService.anonymizeDataWithCallback(request).join();

        var recordedRequest = mockWebServer.takeRequest();
        assertEquals("POST", recordedRequest.getMethod());
        assertEquals("/test/callback", recordedRequest.getPath());
    }

    @Test
    public void testSendCallback() throws IOException, InterruptedException {
        // Enqueue a mock response
        mockWebServer.enqueue(new MockResponse().setBody("ok").setResponseCode(200));

        // Create a mock DataSet
        DataSet mockDataSet = generateDataSetWithConfig();

        // Get the full URL of the mock server
        String mockUrl = mockWebServer.url("/callback/success").toString();

        // Call the method to send the callback
        anonymizationService.sendCallback(mockUrl, dataSet);

        // Verify the request
        var recordedRequest = mockWebServer.takeRequest();
        assertEquals("POST", recordedRequest.getMethod());
        assertEquals("/callback/success", recordedRequest.getPath());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, recordedRequest.getHeader("Content-Type"));

        // Convert the request body back to DataSet and verify its content
//        DataSet receivedDataSet = objectMapper.readValue(recordedRequest.getBody().readUtf8(), DataSet.class);
//        assertNotNull(receivedDataSet);
//        // Log the received data for verification
//        System.out.println("Received DataSet in callback: " + receivedDataSet);
    }
}
