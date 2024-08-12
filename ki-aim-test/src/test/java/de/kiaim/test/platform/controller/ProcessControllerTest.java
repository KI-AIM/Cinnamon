package de.kiaim.test.platform.controller;

import de.kiaim.platform.model.dto.SynthetizationResponse;
import de.kiaim.platform.model.entity.ExternalProcessEntity;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.test.platform.ControllerTest;
import de.kiaim.test.util.DataConfigurationTestHelper;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithUserDetails("test_user")
public class ProcessControllerTest extends ControllerTest {

	private static final int mockBackEndPort = TestSocketUtils.findAvailableTcpPort();

	@Value("${ki-aim.steps.SYNTHETIZATION.callbackHost}")
	private String callbackHost;

	private MockWebServer mockBackEnd;

	@DynamicPropertySource
	static void dynamicProperties(DynamicPropertyRegistry registry) {
		registry.add("ki-aim.steps.synthetization.url", () -> String.format("http://localhost:%s", mockBackEndPort));
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
	public void startAndFinishProcess() throws Exception {
		postData(false);

		final SynthetizationResponse response = new SynthetizationResponse();
		response.setPid("123");
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .body(jsonMapper.writeValueAsString(response))
				                    .build());

		mockMvc.perform(post("/api/process/start")
				                .param("stepName", Step.SYNTHETIZATION.name())
				                .param("url", "/start_synthetization_process/ctgan")
				                .param("configurationName", "configurationName")
				                .param("configuration", "configuration")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isOk())
		       .andExpect(content().string("\"" + ProcessStatus.RUNNING.name() + "\""));

		// Test state changes
		var updateTestProject = getTestProject();
		var process = updateTestProject.getProcesses().get(Step.SYNTHETIZATION);
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

		final Long id = process.getId();
		final String callbackUrl = "http://" + callbackHost + ":8080/api/process/" + id.toString() + "/callback";

		// Test request content
		for (final FileItem fileItem : fileItems) {
			if (fileItem.getFieldName().equals("data")) {
				assertEquals(ResourceHelper.loadCsvFileAsString(), fileItem.getString(),
				             "Unexpected content of data!");
			} else if (fileItem.getFieldName().equals("attribute_config")) {
				assertEquals(DataConfigurationTestHelper.generateDataConfigurationAsYaml(), fileItem.getString(),
				             "Unexpected content of attribute config!");
			} else if (fileItem.getFieldName().equals("algorithm_config")) {
				assertEquals("configuration", fileItem.getString(), "Unexpected session key!");
			} else if (fileItem.getFieldName().equals("session_key")) {
				assertEquals(id.toString(), fileItem.getString(), "Unexpected session key!");
			} else if (fileItem.getFieldName().equals("callback")) {
				assertEquals(callbackUrl, fileItem.getString(), "Unexpected callback URL!");
			} else {
				fail("Unexpected field: " + fileItem.getFieldName());
			}
		}

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

		// Test state changes
		process = updateTestProject.getProcesses().get(Step.SYNTHETIZATION);
		assertEquals(ProcessStatus.FINISHED, process.getExternalProcessStatus(),
		             "External process status has not been updated!");
		assertNotNull(process.getResultDataSet(), "Result has not been set!");
		assertEquals("data", new String(process.getResultDataSet(), StandardCharsets.UTF_8),
		             "Result has not been set correctly!");
		assertTrue(process.getAdditionalResultFiles().containsKey("additional.txt"),
		           "Additional result has not been set!");
		assertEquals("info",
		             new String(process.getAdditionalResultFiles().get("additional.txt"), StandardCharsets.UTF_8),
		             "Additional result has not been set correctly!");
	}

	@Test
	public void startAndCancelProcess() throws Exception {
		postData(false);

		final SynthetizationResponse response = new SynthetizationResponse();
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

		mockMvc.perform(post("/api/process/start")
				                .param("stepName", Step.SYNTHETIZATION.name())
				                .param("url", "/start_synthetization_process/ctgan")
				                .param("configurationName", "configurationName")
				                .param("configuration", "configuration")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isOk())
		       .andExpect(content().string("\"" + ProcessStatus.RUNNING.name() + "\""));

		mockMvc.perform(multipart("/api/process/cancel")
				                .param("stepName", Step.SYNTHETIZATION.name()))
		       .andExpect(status().isOk())
		       .andExpect(content().string("\"" + ProcessStatus.CANCELED.name() + "\""));

		// Test state changes
		final ExternalProcessEntity process = getTestProject().getProcesses().get(Step.SYNTHETIZATION);
		assertEquals(ProcessStatus.CANCELED, process.getExternalProcessStatus(),
		             "External process status has not been updated!");
	}

	@Test
	public void startInvalidStepName() throws Exception {
		postData(false);

		mockMvc.perform(post("/api/process/start")
				                .param("stepName", "invalid")
				                .param("url", "/start_synthetization_process/ctgan")
				                .param("configurationName", "configurationName")
				                .param("configuration", "configuration")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage(
				       "The step 'invalid' is not defined!"));
	}

	@Test
	public void startNoData() throws Exception {
		mockMvc.perform(post("/api/process/start")
				                .param("stepName", "synthetization")
				                .param("url", "/start_synthetization_process/ctgan")
				                .param("configurationName", "configurationName")
				                .param("configuration", "configuration")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage(
				       "No configuration for the project with the given ID '" + getTestProject().getId() + "' found!"));
	}

	@Test
	public void startModuleUnavailable() throws Exception {
		postData(false);

		final SynthetizationResponse response = new SynthetizationResponse();
		response.setMessage("Not found");
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(404)
				                    .body(jsonMapper.writeValueAsString(response))
				                    .build());

		mockMvc.perform(post("/api/process/start")
				                .param("stepName", "synthetization")
				                .param("url", "/start_synthetization_process/ctgan")
				                .param("configurationName", "configurationName")
				                .param("configuration", "configuration2")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isInternalServerError())
		       .andExpect(errorMessage(
				       "Failed to start the process! Got status of 404 NOT_FOUND with message: 'Not found'"));
	}

	@Test
	public void cancelInvalidStepName() throws Exception {
		mockMvc.perform(multipart("/api/process/cancel")
				                .param("stepName", "Invalid"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("The step 'Invalid' is not defined!"));
	}

	@Test
	public void cancelNotStarted() throws Exception {
		mockMvc.perform(multipart("/api/process/cancel")
				                .param("stepName", Step.SYNTHETIZATION.name()))
		       .andExpect(status().isOk())
		       .andExpect(content().string("\"" + ProcessStatus.NOT_STARTED.name() + "\""));
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
