package de.kiaim.cinnamon.anonymization.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.anonymization.AbstractAnonymizationTests;
import de.kiaim.cinnamon.anonymization.model.AnonymizationRequest;
import de.kiaim.cinnamon.model.configuration.anonymization.frontend.FrontendAnonConfigWrapper;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.model.dto.ErrorRequest;
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

@SpringBootTest
public class AnonymizationServiceTest extends AbstractAnonymizationTests {

    @Autowired
    private AnonymizationService anonymizationService;

    @Autowired
    private ObjectMapper objectMapper;

    private FrontendAnonConfigWrapper heartFrontendAnonConfigMissingAttr;

    @BeforeEach
    void setUpService() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String heartFrontendAnonConfigMissingAttrPath = "data/data.heart-failure-anon-config-missing-attribute.yml";
        heartFrontendAnonConfigMissingAttr = importFrontendAnonConfig(heartFrontendAnonConfigMissingAttrPath);
//        System.out.println("heartFrontendAnonConfigMissingAttr " + heartFrontendAnonConfigMissingAttr.getAnonymization());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

//    @Test
//    public void testAnonymizationService() throws Exception {
//
//        Future<DataSet> future = anonymizationService.anonymizeData(
//                dataSet, frontendAnonConfig.getAnonymization(), "processIdTest");
//
//        if (!future.isDone()) {
//            for (int i = 0; i<30; i++) {
//                Thread.sleep(100);
//            }
//        }
//
//        try {
//            DataSet anonymizedDataset = future.get();
//            assertNotNull(anonymizedDataset);
////            System.out.println(anonymizedDataset.getDataRows());
//
//
//        } catch (ExecutionException | InterruptedException e) {
//            e.printStackTrace();
//            throw e;
//        }
//    }

    @Test
    public void testAnonymizationService_MissingAttribute() throws Exception {

        Future<DataSet> future = anonymizationService.anonymizeData(
                heartDataset, heartFrontendAnonConfigMissingAttr.getAnonymization(), "processIdMissing");

        if (!future.isDone()) {
            for (int i = 0; i<30; i++) {
                Thread.sleep(100);
            }
        }

        try {
            DataSet anonymizedDataset = future.get();
            assertNotNull(anonymizedDataset);

        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    public void testAnonymizationService_repeat() throws Exception {
        ClassLoader classLoader = AnonymizationServiceTest.class.getClassLoader();

        String heartDatasetPath = "data.json-dataset-heart-failure.json";
        String heartFrontendAnonConfigPath = "src/test/resources/anonymization.yaml";

        var processId = "testProcess123";
        var heartDataset = objectMapper.readValue(classLoader.getResourceAsStream(heartDatasetPath), DataSet.class);
        var heartFrontendAnonConfig = importFrontendAnonConfig(heartFrontendAnonConfigPath);
        var mockUrl = mockWebServer.url("/test/callback").toString();
        var request = new AnonymizationRequest(processId, heartDataset, heartFrontendAnonConfig.getAnonymization(), mockUrl);

        for (int j = 0; j < 10; j++) {
            mockWebServer.enqueue(new MockResponse().setBody("ok").setResponseCode(200));
            Future<DataSet> future = anonymizationService.anonymizeDataWithCallbackResult(request);
            var result = future.get();
            assertNotNull(result, "Iteration " + j + " returned no result.");
        }
    }

//    @Test
//    public void testAnonymizationServiceOnHeartDataset() throws Exception {
//
////        System.out.println("Dataset heart " + heartDataset );
////        System.out.println("Heart Dataset Frontend Anon Config "+ heartFrontendAnonConfig);
//        Future<DataSet> future = anonymizationService.anonymizeData(heartDataset, heartFrontendAnonConfig.getAnonymization(), "processIdTest");
//
//        if (!future.isDone()) {
//            for (int i = 0; i<30; i++) {
//                Thread.sleep(100);
//            }
//        }
//
//        try {
//            DataSet anonymizedDataset = future.get();
//            assertNotNull(anonymizedDataset);
////            System.out.println(anonymizedDataset.getDataRows());
//
//
//        } catch (ExecutionException | InterruptedException e) {
//            e.printStackTrace();
//            throw e;
//        }
//    }

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

        assertEquals("application/json", recordedRequest.getHeader("Content-Type"));

        String body = recordedRequest.getBody().readUtf8();
        var errorRequest =  assertDoesNotThrow(() -> objectMapper.readValue(body, ErrorRequest.class));

        assertEquals("ANON_2_2_2", errorRequest.getErrorCode());
    }


}
