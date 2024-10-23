package de.kiaim.test.platform.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.kiaim.model.dto.ExternalProcessResponse;
import de.kiaim.model.status.synthetization.SynthetizationStatus;
import de.kiaim.model.status.synthetization.SynthetizationStepStatus;
import de.kiaim.platform.model.entity.DataSetEntity;
import de.kiaim.platform.model.entity.ExternalProcessEntity;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.service.ProjectService;
import de.kiaim.platform.service.UserService;
import de.kiaim.test.platform.ControllerTest;
import de.kiaim.test.util.DataConfigurationTestHelper;
import de.kiaim.test.util.DataSetTestHelper;
import de.kiaim.test.util.ResourceHelper;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.apache.commons.fileupload2.core.DiskFileItem;
import org.apache.commons.fileupload2.core.DiskFileItemFactory;
import org.apache.commons.fileupload2.core.FileItem;
import org.apache.commons.fileupload2.core.FileItemFactory;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithUserDetails("test_user")
public class ProcessControllerTest extends ControllerTest {

	private static final int mockBackEndPort = TestSocketUtils.findAvailableTcpPort();

	@Value("${ki-aim.steps.SYNTHETIZATION.callbackHost}")
	private String callbackHost;

	private MockWebServer mockBackEnd;

	@Autowired private UserService userService;
	@Autowired private ProjectService projectService;

	@DynamicPropertySource
	static void dynamicProperties(DynamicPropertyRegistry registry) {
		registry.add("ki-aim.steps.synthetization.url", () -> String.format("http://localhost:%s", mockBackEndPort));
		registry.add("ki-aim.steps.anonymization.url", () -> String.format("http://localhost:%s", mockBackEndPort));
	}

	@BeforeEach
	void setUpMockWebServer() throws IOException {
		mockBackEnd = new MockWebServer();
		mockBackEnd.start(mockBackEndPort);
	}

	@AfterEach
	void shutDownMockWebServer() throws IOException {
		mockBackEnd.shutdown();
	}

	@Test
	public void getProcessNotStarted() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/api/process/execution"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("status").value(ProcessStatus.NOT_STARTED.name()))
		       .andExpect(jsonPath("currentStep").value(nullValue()));
	}

	@Test
	public void configure() throws Exception {
		mockMvc.perform(post("/api/process/execution/configure")
				                .param("stepName", Step.ANONYMIZATION.name())
				                .param("url", "/start_synthetization_process/ctgan")
				                .param("configuration", "configuration")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isOk());
		mockMvc.perform(post("/api/process/execution/configure")
				                .param("stepName", Step.SYNTHETIZATION.name())
				                .param("url", "/start_synthetization_process/ctgan")
				                .param("configuration", "configuration")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isOk());
	}

	@Test
	public void startAndFinishProcess() throws Exception {
		// Setup
		postData(false);
		configure();

		// All requests for one process
		start();
		getStatus1();
		finish1();
		getStatus2();
		finish2();
		getStatus3();
	}

	private void start() throws Exception {
		final ExternalProcessResponse response = new ExternalProcessResponse();
		response.setPid("123");
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .body(jsonMapper.writeValueAsString(response))
				                    .build());

		mockMvc.perform(post("/api/process/execution/start"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("status").value(ProcessStatus.RUNNING.name()))
		       .andExpect(jsonPath("currentStep").value(Step.ANONYMIZATION.name()))
		       .andExpect(jsonPath("processes.ANONYMIZATION.externalProcessStatus").value(ProcessStatus.RUNNING.name()))
		       .andExpect(jsonPath("processes.ANONYMIZATION.status").value(nullValue()))
		       .andExpect(jsonPath("processes.SYNTHETIZATION.externalProcessStatus").value(
				       ProcessStatus.NOT_STARTED.name()))
		       .andExpect(jsonPath("processes.SYNTHETIZATION.status").value(nullValue()));

		// Test state changes
		var updateTestProject = getTestProject();
		var process = updateTestProject.getExecutions().get(Step.EXECUTION).getProcesses().get(Step.ANONYMIZATION);
		assertNotNull(process, "No external process has been created!");
		assertEquals(ProcessStatus.RUNNING, process.getExternalProcessStatus(),
		             "External process status has not been updated!");

		// Test request
		RecordedRequest recordedRequest = mockBackEnd.takeRequest();
		assertEquals("POST", recordedRequest.getMethod());
		assertEquals("/start_synthetization_process/ctgan", recordedRequest.getPath());

		// Transform request into ServletRequest so FileUpload can parse it
		final byte[] body = recordedRequest.getBody().readByteArray();
		final var request = new MockHttpServletRequest();
		request.setContent(body);
		request.setContentType(recordedRequest.getHeaders().get("Content-Type"));

		// Parse request
		final FileItemFactory<DiskFileItem> factory = DiskFileItemFactory.builder().get();
		final var upload = new JakartaServletFileUpload(factory);
		final List<DiskFileItem> fileItems = upload.parseRequest(request);

		Long id = process.getId();
		final String callbackUrl = "http://" + callbackHost + ":8080/api/process/" + id.toString() + "/callback";

		// Test request content
		for (final FileItem fileItem : fileItems) {
			if (fileItem.getFieldName().equals("data")) {
				assertEquals(DataSetTestHelper.generateDataSetAsJson(false), fileItem.getString(),
				             "Unexpected content of data!");
			} else if (fileItem.getFieldName().equals("session_key")) {
				assertEquals(id.toString(), fileItem.getString(), "Unexpected session key!");
			} else if (fileItem.getFieldName().equals("callback")) {
				assertEquals(callbackUrl, fileItem.getString(), "Unexpected callback URL!");
			} else if (fileItem.getFieldName().equals("anonymizationConfig")) {
				assertEquals("\"configuration\"", fileItem.getString(), "Unexpected anonymization config!");
			} else {
				fail("Unexpected field: " + fileItem.getFieldName());
			}
		}

	}

	private void getStatus1() throws Exception {
		var updateTestProject = getTestProject();
		var process = updateTestProject.getExecutions().get(Step.EXECUTION).getProcesses().get(Step.ANONYMIZATION);
		Long id = process.getId();

		// Get status
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .body("status")
				                    .build());
		mockMvc.perform(MockMvcRequestBuilders.get("/api/process/execution"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("status").value(ProcessStatus.RUNNING.name()))
		       .andExpect(jsonPath("currentStep").value(Step.ANONYMIZATION.name()))
		       .andExpect(jsonPath("processes.ANONYMIZATION.externalProcessStatus").value(ProcessStatus.RUNNING.name()))
		       .andExpect(jsonPath("processes.ANONYMIZATION.status").value("status"))
		       .andExpect(jsonPath("processes.SYNTHETIZATION.externalProcessStatus").value(
				       ProcessStatus.NOT_STARTED.name()))
		       .andExpect(jsonPath("processes.SYNTHETIZATION.status").value(nullValue()));

		var recordedRequest = mockBackEnd.takeRequest();
		assertEquals("GET", recordedRequest.getMethod());
		assertEquals("/api/anonymization/process/" + id + "/status", recordedRequest.getPath());
	}

	private void finish1() throws Exception {
		var updateTestProject = getTestProject();
		var process = updateTestProject.getExecutions().get(Step.EXECUTION).getProcesses().get(Step.ANONYMIZATION);
		Long id = process.getId();

		final ExternalProcessResponse response = new ExternalProcessResponse();
		response.setPid("123");

		// Send callback request
		final MockMultipartFile resultData = new MockMultipartFile("synthetic_data", "csv.csv",
		                                                           MediaType.TEXT_PLAIN_VALUE,
		                                                           "data".getBytes());
		final MockMultipartFile resultAdditional = new MockMultipartFile("additional_data", "additional.txt",
		                                                                 MediaType.TEXT_PLAIN_VALUE,
		                                                                 "info".getBytes());
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .body(jsonMapper.writeValueAsString(response))
				                    .build());
		mockMvc.perform(multipart("/api/process/" + id + "/callback")
				                .file(resultData)
				                .file(resultAdditional))
		       .andExpect(status().isOk());
		var recordedRequest = mockBackEnd.takeRequest();
		assertEquals("POST", recordedRequest.getMethod());
		assertEquals("/start_synthetization_process/ctgan", recordedRequest.getPath());

		// Test state changes
		process = updateTestProject.getExecutions().get(Step.EXECUTION).getProcesses().get(Step.ANONYMIZATION);
		final DataSetEntity dataSetEntity = updateTestProject.getDataSets().get(Step.ANONYMIZATION);
		assertEquals(ProcessStatus.FINISHED, process.getExternalProcessStatus(),
		             "External process status has not been updated!");
		assertTrue(existsDataSet(dataSetEntity.getId()), "Dataset has not been stored!");
		assertTrue(dataSetEntity.isStoredData(), "Dataset has not been stored!");
		assertTrue(process.getAdditionalResultFiles().containsKey("additional.txt"),
		           "Additional result has not been set!");
		assertEquals("info",
		             new String(process.getAdditionalResultFiles().get("additional.txt"), StandardCharsets.UTF_8),
		             "Additional result has not been set correctly!");

	}

	private void getStatus2() throws Exception {
		var updateTestProject = getTestProject();
		var process = updateTestProject.getExecutions().get(Step.EXECUTION).getProcesses().get(Step.SYNTHETIZATION);
		Long id = process.getId();

		enqueueSynthStatus();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/process/execution"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("status").value(ProcessStatus.RUNNING.name()))
		       .andExpect(jsonPath("currentStep").value(Step.SYNTHETIZATION.name()))
		       .andExpect(jsonPath("processes.ANONYMIZATION.externalProcessStatus").value(ProcessStatus.FINISHED.name()))
		       .andExpect(jsonPath("processes.ANONYMIZATION.status").value("status"))
		       .andExpect(jsonPath("processes.SYNTHETIZATION.externalProcessStatus").value(
				       ProcessStatus.RUNNING.name()))
		       .andExpect(jsonPath("processes.SYNTHETIZATION.status").value("{\"status\":[{\"completed\":\"False\",\"duration\":null,\"step\":\"callback\",\"remaining_time\":null}],\"session_key\":null,\"synthesizer_name\":null}"));
		var recordedRequest = mockBackEnd.takeRequest();
		assertEquals("GET", recordedRequest.getMethod());
		assertEquals("/get_status/" + id, recordedRequest.getPath());
	}

	private void finish2() throws Exception {
		var updateTestProject = getTestProject();
		var process = updateTestProject.getExecutions().get(Step.EXECUTION).getProcesses().get(Step.SYNTHETIZATION);
		Long id = process.getId();

		enqueueSynthStatus();

		final MockMultipartFile resultData = new MockMultipartFile("synthetic_data", "csv.csv",
		                                                           MediaType.TEXT_PLAIN_VALUE,
		                                                           "data".getBytes());
		final MockMultipartFile resultAdditional = new MockMultipartFile("additional_data", "additional.txt",
		                                                                 MediaType.TEXT_PLAIN_VALUE,
		                                                                 "info".getBytes());
		mockMvc.perform(multipart("/api/process/" + id + "/callback")
				                .file(resultData)
				                .file(resultAdditional))
		       .andExpect(status().isOk());
	}

	private void getStatus3() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/api/process/execution"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("status").value(ProcessStatus.FINISHED.name()))
		       .andExpect(jsonPath("currentStep").value(nullValue()))
		       .andExpect(jsonPath("processes.ANONYMIZATION.externalProcessStatus").value(ProcessStatus.FINISHED.name()))
		       .andExpect(jsonPath("processes.ANONYMIZATION.status").value("status"))
		       .andExpect(jsonPath("processes.SYNTHETIZATION.externalProcessStatus").value(
				       ProcessStatus.FINISHED.name()))
		       .andExpect(jsonPath("processes.SYNTHETIZATION.status").value("{\"status\":[{\"completed\":\"True\",\"duration\":null,\"step\":\"callback\",\"remaining_time\":null}],\"session_key\":null,\"synthesizer_name\":null}"));
	}

	private void enqueueSynthStatus() throws JsonProcessingException {
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

	@Test
	public void startAndCancelProcess() throws Exception {
		postData(false);
		configure();

		final ExternalProcessResponse response = new ExternalProcessResponse();
		response.setPid("123");
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .body(jsonMapper.writeValueAsString(response))
				                    .build());
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .body(jsonMapper.writeValueAsString(response))
				                    .build());

		mockMvc.perform(post("/api/process/execution/start"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("status").value(ProcessStatus.RUNNING.name()))
		       .andExpect(jsonPath("currentStep").value(Step.ANONYMIZATION.name()));

		mockMvc.perform(multipart("/api/process/execution/cancel")
				                .param("stepName", Step.SYNTHETIZATION.name()))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("status").value(ProcessStatus.CANCELED.name()))
		       .andExpect(jsonPath("currentStep").value(nullValue()));

		// Test state changes
		final ExternalProcessEntity process = getTestProject().getExecutions().get(Step.EXECUTION).getProcesses().get(Step.ANONYMIZATION);
		assertEquals(ProcessStatus.CANCELED, process.getExternalProcessStatus(),
		             "External process status has not been updated!");
	}

	@Test
	public void startSchedule() throws Exception {
		// Start first
		postData(false);
		configure();

		final ExternalProcessResponse response = new ExternalProcessResponse();
		response.setPid("1");
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .body(jsonMapper.writeValueAsString(response))
				                    .build());

		mockMvc.perform(post("/api/process/execution/start")
				                .with(httpBasic("test_user", "password")))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("status").value(ProcessStatus.RUNNING.name()));

		// Start second
		final var user = userService.save("test_user_3", "changeme");
		var project = projectService.createProject(user);
		postData(false, "test_user_3");
		mockMvc.perform(post("/api/process/execution/configure")
				                .with(httpBasic("test_user_3", "changeme"))
				                .param("stepName", Step.ANONYMIZATION.name())
				                .param("url", "/start_synthetization_process/ctgan")
				                .param("configuration", "configuration")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isOk());
		mockMvc.perform(post("/api/process/execution/configure")
				                .with(httpBasic("test_user_3", "changeme"))
				                .param("stepName", Step.SYNTHETIZATION.name())
				                .param("url", "/start_synthetization_process/ctgan")
				                .param("configuration", "configuration")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isOk());


		mockMvc.perform(post("/api/process/execution/start")
				                .with(httpBasic("test_user_3", "changeme")))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("status").value(ProcessStatus.RUNNING.name()))
		       .andExpect(jsonPath("processes.ANONYMIZATION.externalProcessStatus").value(ProcessStatus.SCHEDULED.name()));

		// Cancel first
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .build());
		response.setPid("2");
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .body(jsonMapper.writeValueAsString(response))
				                    .build());
		mockMvc.perform(multipart("/api/process/execution/cancel")
				                .with(httpBasic("test_user", "password"))
				                .param("stepName", Step.SYNTHETIZATION.name()))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("status").value(ProcessStatus.CANCELED.name()));

		project = projectService.getProject(user);

		// Check if the second process is running
		assertEquals(ProcessStatus.RUNNING, project.getExecutions().get(Step.EXECUTION).getProcesses().get(Step.ANONYMIZATION).getExternalProcessStatus());
	}

	@Test
	public void startAndError() throws Exception {
		// Setup
		postData(false);
		configure();

		// Start
		final ExternalProcessResponse response = new ExternalProcessResponse();
		response.setPid("1");
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .body(jsonMapper.writeValueAsString(response))
				                    .build());

		mockMvc.perform(post("/api/process/execution/start")
				                .with(httpBasic("test_user", "password")))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("status").value(ProcessStatus.RUNNING.name()));


		var updateTestProject = getTestProject();
		var process = updateTestProject.getExecutions().get(Step.EXECUTION).getProcesses().get(Step.ANONYMIZATION);
		Long id = process.getId();


		// Send callback request with error
		final MockMultipartFile resultData = new MockMultipartFile("exception_message", "exception_message.txt",
		                                                           MediaType.TEXT_PLAIN_VALUE,
		                                                           "An error occurred!".getBytes());
		mockMvc.perform(multipart("/api/process/" + id + "/callback")
				                .file(resultData))
				                .andExpect(status().isOk());
		var recordedRequest = mockBackEnd.takeRequest();
		assertEquals("POST", recordedRequest.getMethod());
		assertEquals("/start_synthetization_process/ctgan", recordedRequest.getPath());

		// Test state changes
		process = updateTestProject.getExecutions().get(Step.EXECUTION).getProcesses().get(Step.ANONYMIZATION);
		assertEquals(ProcessStatus.ERROR, process.getExecutionStep().getStatus());
		assertEquals(ProcessStatus.ERROR, process.getExternalProcessStatus(),
		             "External process status has not been updated!");
		assertEquals("An error occurred!", process.getStatus());
		assertFalse(process.getAdditionalResultFiles().containsKey("exception_message.txt"),
		           "Exception message should not have been set!");
	}

	@Test
	public void startNoData() throws Exception {
		configure();
		mockMvc.perform(post("/api/process/execution/start"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage(
				       "The project '" + testProject.getId() + "' does not contain a data set for step 'VALIDATION'!"));
	}

	@Test
	public void startNoConfiguration() throws Exception {
		mockMvc.perform(post("/api/process/execution/start"))
		       .andExpect(status().isInternalServerError())
		       .andExpect(errorMessage(
				       "No configuration with name 'anonymization' required for step 'ANONYMIZATION' found!"));
	}

	@Test
	public void startModuleUnavailable() throws Exception {
		postData(false);
		configure();

		final ExternalProcessResponse response = new ExternalProcessResponse();
		response.setMessage("Not found");
		response.setError("Nicht gefunden, but in German");
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(404)
				                    .body(jsonMapper.writeValueAsString(response))
				                    .build());

		mockMvc.perform(post("/api/process/execution/start"))
		       .andExpect(status().isInternalServerError())
		       .andExpect(errorMessage(
				       "Failed to start the process! Got status of 404 NOT_FOUND with message: 'Not found' and error: 'Nicht gefunden, but in German'"));
	}

	@Test
	public void cancelNotStarted() throws Exception {
		mockMvc.perform(post("/api/process/execution/cancel"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("status").value(ProcessStatus.NOT_STARTED.name()));
	}

	@Test
	public void finishInvalidProcess() throws Exception {
		final MockMultipartFile result = new MockMultipartFile("synthetic_data", "result.csv",
		                                                       MediaType.TEXT_PLAIN_VALUE,
		                                                       "result".getBytes());
		mockMvc.perform(multipart("/api/process/0/callback")
				                .file(result))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("No process with the given ID '0' exists!"));
	}

}
