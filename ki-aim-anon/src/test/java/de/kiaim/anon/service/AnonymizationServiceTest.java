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
    public void testAnonymizeDataWithCallback_Success() throws Exception {
        mockWebServer.enqueue(new MockResponse().setBody("ok").setResponseCode(200));

        String localMockUrl = mockWebServer.url("/callback/success").toString();
        AnonymizationRequest anonRequest = new AnonymizationRequest(processId, dataSet, frontendAnonConfig.getAnonymization(), localMockUrl);

        anonymizationService.anonymizeDataWithCallbackResult(anonRequest).join();

        var recordedRequest = mockWebServer.takeRequest();
        assertEquals("POST", recordedRequest.getMethod());
        assertEquals("/callback/success", recordedRequest.getPath());

        // Check content
        assertTrue(recordedRequest.getHeader("Content-Type").contains("multipart/form-data"));

        String body = recordedRequest.getBody().readUtf8();
        assertTrue(body.contains("Content-Disposition: form-data; name=\"synthetic_data\""));

        System.out.println("Received multipart content in callback: " + body);
    }


    @Test
    public void testSendCallbackResult() throws IOException, InterruptedException {
        // Enqueue a mock response
        mockWebServer.enqueue(new MockResponse().setBody("ok").setResponseCode(200));

        // Create a mock DataSet
        DataSet mockDataSet = generateDataSetWithConfig();

        // Get the full URL of the mock server
        String mockUrl = mockWebServer.url("/callback/success").toString();

        anonymizationService.sendCallbackResult(mockUrl, mockDataSet);

        var recordedRequest = mockWebServer.takeRequest();

        // Verify the HTTP method and endpoint
        assertEquals("POST", recordedRequest.getMethod());
        assertEquals("/callback/success", recordedRequest.getPath());

        // Ensure the content type is multipart/form-data
        String contentType = recordedRequest.getHeader("Content-Type");
        assertNotNull(contentType);
        assertTrue(contentType.startsWith("multipart/form-data"));

        String requestBody = recordedRequest.getBody().readUtf8();

        assertTrue(requestBody.contains("Content-Disposition: form-data; name=\"synthetic_data\""));

        System.out.println("Received multipart body: " + requestBody);
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
