package de.kiaim.cinnamon.test.platform.controller;

import de.kiaim.cinnamon.model.dto.ExternalProcessResponse;
import de.kiaim.cinnamon.platform.service.ExternalConfigurationService;
import de.kiaim.cinnamon.test.platform.ControllerTest;
import de.kiaim.cinnamon.test.util.*;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockWebServer
@WithUserDetails("test_user")
public class WorkflowControllerTest extends ControllerTest {

	private MockWebServer mockBackEnd;

	@Autowired private ExternalConfigurationService externalConfigurationService;

	@BeforeEach
	public void setup() {
		externalConfigurationService.setCachedAvailableAlgorithms("anonymization",
		                                                          AlgorithmTestHelper.generateAvailableAlgorithms());
		externalConfigurationService.setCachedAlgorithmDefinition("anonymization", "/algorithmA",
		                                                          AlgorithmTestHelper.generateAlgorithmDefinition());

		externalConfigurationService.setCachedAvailableAlgorithms("synthetization_configuration",
		                                                          AlgorithmTestHelper.generateAvailableAlgorithms2());
		externalConfigurationService.setCachedAlgorithmDefinition("synthetization_configuration", "/algorithm/ctgan",
		                                                          AlgorithmTestHelper.generateAlgorithmDefinition2());
	}

	@Test
	public void testWorkflow() throws Exception {
		// Prepare the data file
		var data = ResourceHelper.loadCsvFile();
		var datafile = new MockMultipartFile("data", "file.csv", null, data.getInputStream());

		// Prepare the configuration file
		final String dataset = """
		                       dataset:
		                         createHoldOutSplit: true
		                         holdOutSplitPercentage: 0.2
		                       """;
		final String pipeline = """
		                        pipeline:
		                          pipelines:
		                          - jobs:
		                            - name: anonymization
		                            - name: synthetization
		                            - name: technical_evaluation
		                            - name: risk_evaluation
		                        """;
		var configuration = ProjectConfigurationTestHelper.generateProjectConfigurationAsYaml() + "\n"
		                    + FileConfigurationTestHelper.generateFileConfigurationAsYaml() + "\n"
		                    + DataConfigurationTestHelper.generateDataConfigurationAsYaml() + "\n"
		                    + dataset + "\n"
		                    + pipeline + "\n"
		                    + AlgorithmTestHelper.generateAlgorithmConfigurationYaml() + "\n"
		                    + AlgorithmTestHelper.generateAlgorithmConfiguration2() + "\n"
		                    + AlgorithmTestHelper.generateAlgorithmConfiguration3() + "\n"
		                    + AlgorithmTestHelper.generateAlgorithmConfiguration4() + "\n";
		var configurationFile = new MockMultipartFile("configuration", "configuration.yaml", null,
		                                              configuration.getBytes());

		// Prepare responses of external modules
		enqueueAnonStartResponse();

		// Send the request
		mockMvc.perform(multipart("/api/workflow").file(datafile).file(configurationFile))
		       .andExpect(status().isOk());
		verifyWorkflow();
		finish1();
		verifyWorkflow();
		enqueueAnonStartResponse();
	}

	private void verifyWorkflow() throws Exception {
		mockMvc.perform(get("/api/workflow"))
		       .andExpect(status().isOk())
		       .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
		       .andExpect(content().json("""
		                                 {
		                                 	"status": "RUNNING",
		                                 	"currentJob": "anonymization",
		                                 	"progress": 0.25
		                                 }
		                                 """));
	}

	private void enqueueAnonStartResponse() {
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .body("status")
				                    .build());
	}

	private void finish1() throws Exception {
		var updateTestProject = getTestProject();

		var process = updateTestProject.getPipelines().get(0).getStageByIndex(0).getProcess(0);
		String id = process.getUuid().toString();

		final ExternalProcessResponse response = new ExternalProcessResponse();
		response.setPid("123");

		// Send callback request
		var anonymizationResult = new MockMultipartFile("anonymized_dataset", "additional.txt",
		                                                MediaType.TEXT_PLAIN_VALUE,
		                                                DataSetTestHelper.generateDataSetAsJson().getBytes());

		final MockMultipartFile resultAdditional = new MockMultipartFile("additional_data", "additional.txt",
		                                                                 MediaType.TEXT_PLAIN_VALUE,
		                                                                 "anon-info".getBytes());
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .body(jsonMapper.writeValueAsString(response))
				                    .build());
		mockMvc.perform(multipart("/api/process/" + id + "/callback")
				                .file(anonymizationResult)
				                .file(resultAdditional)
		       )
		       .andExpect(status().isOk());

		RecordedRequest recordedRequest = mockBackEnd.takeRequest(1, TimeUnit.SECONDS);
		assertNotNull(recordedRequest, "No request has been sent to the server!");
		assertEquals("POST", recordedRequest.getMethod());
		assertEquals("/start_synthetization_process/ctgan", recordedRequest.getPath());
	}

}
