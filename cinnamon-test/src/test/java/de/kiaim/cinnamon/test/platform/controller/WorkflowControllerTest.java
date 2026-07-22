package de.kiaim.cinnamon.test.platform.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.kiaim.cinnamon.model.dto.ExternalProcessResponse;
import de.kiaim.cinnamon.model.status.synthetization.SynthetizationStatus;
import de.kiaim.cinnamon.model.status.synthetization.SynthetizationStepStatus;
import de.kiaim.cinnamon.platform.model.dto.WorkflowInformation;
import de.kiaim.cinnamon.platform.model.entity.DataProcessingEntity;
import de.kiaim.cinnamon.platform.model.enumeration.StepOutputEncoding;
import de.kiaim.cinnamon.platform.repository.ProjectRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockWebServer
@WithUserDetails("test_user")
public class WorkflowControllerTest extends ControllerTest {

	private MockWebServer mockBackEnd;

	@Autowired private ProjectRepository projectRepository;

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

		externalConfigurationService.setCachedAvailableAlgorithms("evaluation_configuration",
		                                                          AlgorithmTestHelper.generateAvailableAlgorithms2());
		externalConfigurationService.setCachedAlgorithmDefinition("evaluation_configuration", "/algorithm/ctgan",
		                                                          AlgorithmTestHelper.generateAlgorithmDefinition2());

		externalConfigurationService.setCachedAvailableAlgorithms("risk_assessment_configuration",
		                                                          AlgorithmTestHelper.generateAvailableAlgorithms2());
		externalConfigurationService.setCachedAlgorithmDefinition("risk_assessment_configuration", "/algorithm/ctgan",
		                                                          AlgorithmTestHelper.generateAlgorithmDefinition2());
	}

	@Test
	public void testWorkflowMissingConfiguration() throws Exception {
		mockMvc.perform(multipart("/api/workflow").file(ResourceHelper.loadCsvFile("data")))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_3_2_1"))
		       .andExpect(validationError("configuration", "Configuration file is required!"));
	}

	@Test
	public void testWorkflowEmptyConfiguration() throws Exception {
		mockMvc.perform(multipart("/api/workflow")
				                .file(ResourceHelper.loadCsvFile("data"))
				                .file(ResourceHelper.emptyFile("configuration")))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_1_14_13"));
	}

	@Test
	public void getWorkflowInvalidId() throws Exception {
		mockMvc.perform(get("/api/workflow/123"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_1_11_3"));
	}

	@Test
	public void getWorkflowNotFound() throws Exception {
		mockMvc.perform(get("/api/workflow/9842d632-3c9c-42a5-bd86-26a2d9db2294"))
		       .andExpect(status().isNotFound())
		       .andExpect(errorCode("PLATFORM_1_17_1"));
	}

	@Test
	public void testWorkflow() throws Exception {
		// Prepare the data file
		var data = ResourceHelper.loadCsvFile();
		var datafile = new MockMultipartFile("data", "file.csv", null, data.getInputStream());

		// Prepare the configuration file
		var configurationFile = prepareConfigurationFile();

		// Prepare responses of external modules
		enqueueAnonStartResponse();
		enqueueAnonStatusResponse();

		String expectedJson = """
		                      {
		                        "currentStageIndex":0,
		                        "stages":[{
		                          "stageName":"execution",
		                          "status":"RUNNING",
		                          "currentProcessIndex":0,
		                          "processes":[{
		                            "externalProcessStatus":"RUNNING",
		                            "step":"anonymization",
		                            "status":"status",
		                            "processSteps":null
		                            },{
		                            "externalProcessStatus":"NOT_STARTED",
		                            "step":"synthetization",
		                            "status":null,
		                            "processSteps":null
		                          }]
		                          },{
		                          "stageName":"evaluation",
		                          "status":"NOT_STARTED",
		                          "currentProcessIndex":null,
		                          "processes":[{
		                            "externalProcessStatus":"NOT_STARTED",
		                            "step":"technical_evaluation",
		                            "status":null,
		                            "processSteps":[]
		                            },{
		                            "externalProcessStatus":"NOT_STARTED",
		                            "step":"risk_evaluation",
		                            "status":null,
		                            "processSteps":[]
		                            },{
		                            "externalProcessStatus":"NOT_STARTED",
		                            "step":"risk_evaluation_o",
		                            "status":null,
		                            "processSteps":[]
		                          }]
		                        }]
		                      }
		                      """;
		Object expectedPipeline;
		try {
			expectedPipeline = jsonMapper.readValue(expectedJson, Object.class);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}

		// Send the request
		var result = mockMvc.perform(multipart("/api/workflow").file(datafile).file(configurationFile))
		                    .andExpect(status().isAccepted())
		                    .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
		                    .andExpect(jsonPath("pipeline").value(expectedPipeline))
		                    .andReturn();
		result.getResponse().getContentAsString();
		WorkflowInformation workflowInformation = jsonMapper.readValue(result.getResponse().getContentAsString(),
		                                                               WorkflowInformation.class);
		String workflowId = workflowInformation.getWorkflowId();

		verifyProcessStartRequest("/algorithmA");
		verifyProcessStatusRequest();
		verifyWorkflow(workflowId, 0, true);
		finish(workflowId, 0, 0, "/start_synthetization_process/ctgan");
		verifyWorkflow(workflowId, 0, true);
		finish(workflowId, 0, 1, "/start_synthetization_process/ctgan");
		verifyWorkflow(workflowId, 1, false);
		finish(workflowId, 1, 0, "");
		verifyWorkflow(workflowId, 1, false);
		finish(workflowId, 1, 1, null);
		verifyWorkflow(workflowId, null, false);
		deleteWorkflow(workflowId);
	}

	private void verifyWorkflow(String workflowId, Integer expectedCurrentStage,
	                            boolean expectedHasStatusRequest) throws Exception {
		if (expectedHasStatusRequest) {
			enqueueAnonStatusResponse();
		}
		mockMvc.perform(get("/api/workflow/" + workflowId))
		       .andExpect(status().isOk())
		       .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
		       .andExpect(jsonPath("pipeline.currentStageIndex").value(expectedCurrentStage));
		if (expectedHasStatusRequest) {
			verifyProcessStatusRequest();
		}
	}

	private void verifyProcessStartRequest(String algorithm) throws InterruptedException {
		RecordedRequest recordedRequest = mockBackEnd.takeRequest(1, TimeUnit.SECONDS);
		assertNotNull(recordedRequest, "No start request has been sent to the server!");
		assertEquals("POST", recordedRequest.getMethod());
		if (!algorithm.isEmpty()) {
			assertEquals(algorithm, recordedRequest.getPath());
		}
	}

	private void verifyProcessStatusRequest() throws InterruptedException {
		RecordedRequest recordedRequest = mockBackEnd.takeRequest(1, TimeUnit.SECONDS);
		assertNotNull(recordedRequest, "No status request has been sent to the server!");
		assertEquals("GET", recordedRequest.getMethod());
	}

	private MockMultipartFile prepareConfigurationFile() {
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
		                    + FileConfigurationTestHelper.generateDataSourceConfigurationAsYaml() + "\n"
		                    + FileConfigurationTestHelper.generateFileConfigurationAsYaml() + "\n"
		                    + DataConfigurationTestHelper.generateDataConfigurationAsYaml() + "\n"
		                    + dataset + "\n"
		                    + pipeline + "\n"
		                    + AlgorithmTestHelper.generateAlgorithmConfigurationYaml() + "\n"
		                    + AlgorithmTestHelper.generateAlgorithmConfiguration2() + "\n"
		                    + AlgorithmTestHelper.generateAlgorithmConfiguration3() + "\n"
		                    + AlgorithmTestHelper.generateAlgorithmConfiguration4() + "\n";
		return new MockMultipartFile("configuration", "configuration.yaml", null, configuration.getBytes());
	}

	private void enqueueAnonStartResponse() {
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .body("""
				                          {
				                            "message": "Stated process"
				                          }
				                          """)
				                    .build());
	}

	private void finish(String workflowId, int stageIndex, int processIndex, String nextAlgorithm) throws Exception {
		var updateTestProject = getTestUser().getWorkflow(UUID.fromString(workflowId)).getProject();

		var process = updateTestProject.getPipelines().get(0).getStageByIndex(stageIndex).getProcess(processIndex);
		assertNotNull(process.getUuid(), "No UUID has been assigned to the process!");
		String id = process.getUuid().toString();

		final ExternalProcessResponse response = new ExternalProcessResponse();
		response.setPid("123");

		// Enqueue responses of external modules
		boolean isFixStatus = process.getJob().isFixStatus()
		                      && !process.getJob().getEndpoint().getStatusEndpoint().isEmpty();
		if (isFixStatus) {
			enqueueSynthStatusResponse();
		}
		if (nextAlgorithm != null) {
			mockBackEnd.enqueue(new MockResponse.Builder()
					                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
					                    .code(200)
					                    .body(jsonMapper.writeValueAsString(response))
					                    .build());
		}

		// Send callback request
		var r = multipart("/api/process/" + id + "/callback");
		for (var abc : process.getJob().getEndpoint().getOutputs()) {
			if (abc.getEncoding() == StepOutputEncoding.DATA_SET) {
				var anonymizationResult = new MockMultipartFile(abc.getPartName(), "additional.json",
				                                                MediaType.APPLICATION_JSON_VALUE,
				                                                DataSetTestHelper.generateDataSetAsJson().getBytes());
				r.file(anonymizationResult);
			} else if (abc.getEncoding() == StepOutputEncoding.DATA) {
				r.file(ResourceHelper.loadCsvFile(abc.getPartName()));
			}
		}

		mockMvc.perform(r).andExpect(status().isOk());

		// Verify requests from the platform to external modules
		if (isFixStatus) {
			verifyProcessStatusRequest();
		}
		if (nextAlgorithm != null) {
			verifyProcessStartRequest(nextAlgorithm);
		}
	}

	private void deleteWorkflow(String workflowId) throws Exception {
		var datasetIds = new ArrayList<Long>();
		var project = getTestUser().getWorkflow(UUID.fromString(workflowId)).getProject();

		if (project.getOriginalData().getDataSet() != null) {
			datasetIds.add(project.getOriginalData().getDataSet().getId());
		}

		final var firstStage = project.getPipelines().get(0).getStageByIndex(0);
		for (final var process : firstStage.getProcesses()) {
			if (process instanceof DataProcessingEntity p) {
				if (p.getDataSet() != null) {
					datasetIds.add(p.getDataSet().getId());
				}
			}
		}

		var projectId = project.getId();

		mockMvc.perform(delete("/api/workflow/" + workflowId))
		       .andExpect(status().isOk());

		// Test if cleanup was successful
		var user = getTestUser();
		assertTrue(user.getWorkflows().isEmpty(), "Workflow has not been deleted!");
		assertFalse(projectRepository.existsById(projectId), "Project has not been deleted!");

		for (var datasetId : datasetIds) {
			assertFalse(existsTable(datasetId), "Dataset has not been deleted!");
		}
	}

	private void enqueueAnonStatusResponse() {
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .body("status")
				                    .build());
	}

	private void enqueueSynthStatusResponse() throws JsonProcessingException {
		var synthStatus = new SynthetizationStatus();
		var synthStepStatus = new SynthetizationStepStatus();
		synthStepStatus.setStep("callback");
		synthStepStatus.setCompleted("False");
		synthStatus.setStatus(List.of(synthStepStatus));
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .body(jsonMapper.writeValueAsString(synthStatus))
				                    .build());
	}


}
