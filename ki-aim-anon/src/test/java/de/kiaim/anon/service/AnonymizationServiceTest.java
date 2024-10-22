package de.kiaim.anon.service;

import de.kiaim.anon.AbstractAnonymizationTests;
import de.kiaim.anon.model.AnonymizationRequest;
import de.kiaim.model.data.DataSet;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static util.GenerateTestDatasets.generateDataSetWithConfig;

@SpringBootTest
public class AnonymizationServiceTest extends AbstractAnonymizationTests {

    @Autowired
    private AnonymizationService anonymizationService;

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

        Future<DataSet> future = anonymizationService.anonymizeData(dataSet, frontendAnonConfig.getAnonymization(), "processIdTest");

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
    public void testAnonymizationServiceOnHeartDataset() throws Exception {

//        System.out.println("Dataset heart " + heartDataset );
//        System.out.println("Heart Dataset Frontend Anon Config "+ heartFrontendAnonConfig);
        Future<DataSet> future = anonymizationService.anonymizeData(heartDataset, heartFrontendAnonConfig.getAnonymization(), "processIdTest");

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
    public void testAnonymizeDataWithCallback_Failure() throws Exception {
        mockWebServer.enqueue(new MockResponse().setBody("ok").setResponseCode(200));

        frontendAnonConfig.getAnonymization().getPrivacyModels().get(0).getModelConfiguration().setRiskThresholdType("InvalidType");

        String localMockUrl = mockWebServer.url("/callback/failure").toString();
        AnonymizationRequest anonRequest = new AnonymizationRequest(processId, dataSet, frontendAnonConfig.getAnonymization(), localMockUrl);

        anonymizationService.anonymizeDataWithCallbackResult(anonRequest).join();

        var recordedRequest = mockWebServer.takeRequest();

        assertEquals("POST", recordedRequest.getMethod());

        assertEquals("/callback/failure", recordedRequest.getPath());

        assertTrue(recordedRequest.getHeader("Content-Type").startsWith("multipart/form-data"));

        String body = recordedRequest.getBody().readUtf8();
        assertTrue(body.contains("Content-Disposition: form-data; name=\"error_message\""));
        assertTrue(body.contains("Anonymization failed"));

        System.out.println("Received error response in callback: " + body);
    }
}
