package de.kiaim.cinnamon.test.platform.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.kiaim.cinnamon.model.dto.ErrorRequest;
import de.kiaim.cinnamon.model.dto.ExternalProcessResponse;
import de.kiaim.cinnamon.model.serialization.mapper.JsonMapper;
import de.kiaim.cinnamon.model.status.synthetization.SynthetizationStatus;
import de.kiaim.cinnamon.model.status.synthetization.SynthetizationStepStatus;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.configuration.Stage;
import de.kiaim.cinnamon.platform.model.dto.DataSetSource;
import de.kiaim.cinnamon.platform.model.entity.DataSetEntity;
import de.kiaim.cinnamon.platform.model.entity.ExternalProcessEntity;
import de.kiaim.cinnamon.platform.model.enumeration.ProcessStatus;
import de.kiaim.cinnamon.platform.service.DataSetService;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.platform.service.UserService;
import de.kiaim.cinnamon.test.platform.ControllerTest;
import de.kiaim.cinnamon.test.util.DataConfigurationTestHelper;
import de.kiaim.cinnamon.test.util.DataSetTestHelper;
import de.kiaim.cinnamon.test.util.ResourceHelper;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.apache.commons.fileupload2.core.DiskFileItem;
import org.apache.commons.fileupload2.core.DiskFileItemFactory;
import org.apache.commons.fileupload2.core.FileItem;
import org.apache.commons.fileupload2.core.FileItemFactory;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.apache.commons.lang3.tuple.MutablePair;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithUserDetails("test_user")
public class ProcessControllerTest extends ControllerTest {

	private static final String ANON_JOB = "anonymization";
	private static final String SYNTH_JOB = "synthetization";

	private static final int mockBackEndPort = TestSocketUtils.findAvailableTcpPort();

	@Value("${cinnamon.external-server.synthetization-server.callback-host}")
	private String callbackHost;

	private MockWebServer mockBackEnd;

	@Autowired private CinnamonConfiguration cinnamonConfiguration;

	@Autowired private DataSetService dataSetService;
	@Autowired private UserService userService;
	@Autowired private ProjectService projectService;

	@DynamicPropertySource
	static void dynamicProperties(DynamicPropertyRegistry registry) {
		registry.add("cinnamon.external-server.technical-evaluation-server.urlServer", () -> String.format("http://localhost:%s", mockBackEndPort));
		registry.add("cinnamon.external-server.synthetization-server.urlServer", () -> String.format("http://localhost:%s", mockBackEndPort));
		registry.add("cinnamon.external-server.anonymization-server.urlServer", () -> String.format("http://localhost:%s", mockBackEndPort));
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
		       .andExpect(jsonPath("currentProcessIndex").value(nullValue()));
	}

	@Test
	public void configure() throws Exception {
		mockMvc.perform(post("/api/config")
				                .param("configurationName", ANON_JOB)
				                .param("url", "/start_synthetization_process/ctgan")
				                .param("configuration", "configuration")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isOk());
		mockMvc.perform(post("/api/process/execution/configure")
				                .param("jobName", ANON_JOB)
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isOk());

		mockMvc.perform(post("/api/config")
				                .param("configurationName", "synthetization_configuration")
				                .param("url", "/start_synthetization_process/ctgan")
				                .param("configuration", "configuration")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isOk());
		mockMvc.perform(post("/api/process/execution/configure")
				                .param("jobName", SYNTH_JOB)
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isOk());
	}

	@Test
	public void configureSkip() throws Exception {
		mockMvc.perform(post("/api/process/execution/configure")
				                .param("jobName", ANON_JOB)
				                .param("skip", "true")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isOk());
	}

	@Test
	public void configureMissingConfiguration() throws Exception {
		mockMvc.perform(post("/api/process/execution/configure")
				                .param("jobName", ANON_JOB)
				                .param("url", "/start_synthetization_process/ctgan")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_1_8_5"));
	}

	@Test
	public void configureMissingUrl() throws Exception {
		mockMvc.perform(post("/api/config")
				                .param("configurationName", ANON_JOB)
				                .param("configuration", "configuration")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isOk());

		mockMvc.perform(post("/api/process/execution/configure")
				                .param("jobName", ANON_JOB)
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_1_8_5"))
		       .andExpect(errorMessage("No URL for configuration 'anonymization' for job 'anonymization'!"));
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

		// Get results
		getResultFile();
		getResultZip();
	}

	private void start() throws Exception {
		final ExternalProcessResponse response = new ExternalProcessResponse();
		response.setPid("123");
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .body(jsonMapper.writeValueAsString(response))
				                    .build());

		final Stage firstStage = cinnamonConfiguration.getPipeline().getStageList().get(0);

		mockMvc.perform(post("/api/process/" + firstStage.getStageName() + "/start"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("status").value(ProcessStatus.RUNNING.name()))
		       .andExpect(jsonPath("currentProcessIndex").value(0))
		       .andExpect(jsonPath("processes[0].externalProcessStatus").value(ProcessStatus.RUNNING.name()))
		       .andExpect(jsonPath("processes[0].step").value(ANON_JOB))
		       .andExpect(jsonPath("processes[0].status").value(nullValue()))
		       .andExpect(jsonPath("processes[0].processSteps").value(nullValue()))
		       .andExpect(jsonPath("processes[1].externalProcessStatus").value(ProcessStatus.NOT_STARTED.name()))
		       .andExpect(jsonPath("processes[1].step").value(SYNTH_JOB))
		       .andExpect(jsonPath("processes[1].status").value(nullValue()))
		       .andExpect(jsonPath("processes[1].processSteps").value(nullValue()));

		// Test state changes
		var updateTestProject = getTestProject();
		var process = updateTestProject.getPipelines().get(0).getStageByIndex(0).getProcess(0);
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

		String id = process.getUuid().toString();
		final String callbackUrl = "http://" + callbackHost + ":8080/api/process/" + id + "/callback";

		// Test request content
		for (final FileItem fileItem : fileItems) {
			if (fileItem.getFieldName().equals("data")) {
				assertEquals(DataSetTestHelper.generateDataSetAsJson(false), fileItem.getString(),
				             "Unexpected content of data!");
			} else if (fileItem.getFieldName().equals("session_key")) {
				assertEquals(id, fileItem.getString(), "Unexpected session key!");
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
		var process = updateTestProject.getPipelines().get(0).getStageByIndex(0).getProcess(0);
		String id = process.getUuid().toString();

		// Get status
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .body("status")
				                    .build());
		mockMvc.perform(MockMvcRequestBuilders.get("/api/process/execution"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("status").value(ProcessStatus.RUNNING.name()))
		       .andExpect(jsonPath("currentProcessIndex").value(0))
		       .andExpect(jsonPath("processes[0].externalProcessStatus").value(ProcessStatus.RUNNING.name()))
		       .andExpect(jsonPath("processes[0].status").value("status"))
		       .andExpect(jsonPath("processes[0].step").value(ANON_JOB))
		       .andExpect(jsonPath("processes[0].processSteps").value(nullValue()))
		       .andExpect(jsonPath("processes[1].externalProcessStatus").value( ProcessStatus.NOT_STARTED.name()))
		       .andExpect(jsonPath("processes[1].step").value(SYNTH_JOB))
		       .andExpect(jsonPath("processes[1].status").value(nullValue()))
		       .andExpect(jsonPath("processes[1].processSteps").value(nullValue()));


		var recordedRequest = mockBackEnd.takeRequest();
		assertEquals("GET", recordedRequest.getMethod());
		assertEquals("/api/anonymization/process/" + id + "/status", recordedRequest.getPath());
	}

	private void finish1() throws Exception {
		var updateTestProject = getTestProject();

		final Stage firstStage = cinnamonConfiguration.getPipeline().getStageList().get(0);

		var process = updateTestProject.getPipelines().get(0).getStageByIndex(0).getProcess(0);
		String id = process.getUuid().toString();

		final ExternalProcessResponse response = new ExternalProcessResponse();
		response.setPid("123");

		// Send callback request
		var anonymizationResult = new MockMultipartFile("anonymized_dataset", "additional.txt", MediaType.TEXT_PLAIN_VALUE,
		                                DataSetTestHelper.generateDataSetAsJson().getBytes());

		final MockMultipartFile resultAdditional = new MockMultipartFile("additional_data", "additional.txt",
		                                                                 MediaType.TEXT_PLAIN_VALUE,
		                                                                 "info".getBytes());
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
		var recordedRequest = mockBackEnd.takeRequest();
		assertEquals("POST", recordedRequest.getMethod());
		assertEquals("/start_synthetization_process/ctgan", recordedRequest.getPath());

		// Test state changes
		process = updateTestProject.getPipelines().get(0).getStageByIndex(0).getProcess(0);
		final DataSetEntity dataSetEntity = dataSetService.getDataSetEntityOrThrow(updateTestProject, DataSetSource.Job(firstStage.getJobs().get(0)));
		assertEquals(ProcessStatus.FINISHED, process.getExternalProcessStatus(),
		             "External process status has not been updated!");
		assertTrue(existsDataSet(dataSetEntity.getId()), "Dataset has not been stored!");
		assertTrue(dataSetEntity.isStoredData(), "Dataset has not been stored!");
		assertEquals(firstStage.getJobList().subList(0, 1), dataSetEntity.getProcessed(), "Unexpected previous processes!");
		assertTrue(process.getResultFiles().containsKey("additional.txt"),
		           "Additional result has not been set!");
		assertEquals("info", process.getResultFiles().get("additional.txt").getLobString(),
		             "Additional result has not been set correctly!");

	}

	private void getStatus2() throws Exception {
		var updateTestProject = getTestProject();
		var process = updateTestProject.getPipelines().get(0).getStageByIndex(0).getProcess(1);
		String id = process.getUuid().toString();

		enqueueSynthStatus();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/process/execution"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("status").value(ProcessStatus.RUNNING.name()))
		       .andExpect(jsonPath("currentProcessIndex").value(1))
		       .andExpect(jsonPath("processes[0].externalProcessStatus").value(ProcessStatus.FINISHED.name()))
		       .andExpect(jsonPath("processes[0].status").value("status"))
		       .andExpect(jsonPath("processes[0].step").value(ANON_JOB))
		       .andExpect(jsonPath("processes[0].processSteps[0]").value(ANON_JOB))
		       .andExpect(jsonPath("processes[1].externalProcessStatus").value( ProcessStatus.RUNNING.name()))
		       .andExpect(jsonPath("processes[1].status").value("{\"status\":[{\"completed\":\"False\",\"duration\":null,\"step\":\"callback\",\"remaining_time\":null}],\"session_key\":null,\"synthesizer_name\":null}"))
		       .andExpect(jsonPath("processes[1].step").value(SYNTH_JOB))
		       .andExpect(jsonPath("processes[1].processSteps").value(nullValue()));
		var recordedRequest = mockBackEnd.takeRequest();
		assertEquals("GET", recordedRequest.getMethod());
		assertEquals("/get_status/" + id, recordedRequest.getPath());
	}

	private void finish2() throws Exception {
		var updateTestProject = getTestProject();

		final Stage firstStage = cinnamonConfiguration.getPipeline().getStageList().get(0);

		var process = updateTestProject.getPipelines().get(0).getStageByIndex(0).getProcess(1);
		String id = process.getUuid().toString();

		enqueueSynthStatus();

		var resultData = ResourceHelper.loadCsvFile("synthetic_data");
		final MockMultipartFile resultAdditional = new MockMultipartFile("additional_data", "additional.txt",
		                                                                 MediaType.TEXT_PLAIN_VALUE,
		                                                                 "info".getBytes());
		mockMvc.perform(multipart("/api/process/" + id + "/callback")
				                .file(resultData)
				                .file(resultAdditional))
		       .andExpect(status().isOk());

		final DataSetEntity dataSetEntity = dataSetService.getDataSetEntityOrThrow(updateTestProject, DataSetSource.Job(
				firstStage.getJobs().get(1)));
		assertEquals(firstStage.getJobList(), dataSetEntity.getProcessed(), "Unexpected previous processes!");
	}

	private void getStatus3() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/api/process/execution"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("status").value(ProcessStatus.FINISHED.name()))
		       .andExpect(jsonPath("currentProcessIndex").value(nullValue()))
		       .andExpect(jsonPath("processes[0].externalProcessStatus").value(ProcessStatus.FINISHED.name()))
		       .andExpect(jsonPath("processes[0].status").value("status"))
		       .andExpect(jsonPath("processes[0].step").value(ANON_JOB))
		       .andExpect(jsonPath("processes[0].processSteps[0]").value(ANON_JOB))
		       .andExpect(jsonPath("processes[0].processSteps[1]").doesNotExist())
		       .andExpect(jsonPath("processes[1].externalProcessStatus").value( ProcessStatus.FINISHED.name()))
		       .andExpect(jsonPath("processes[1].status").value("{\"status\":[{\"completed\":\"True\",\"duration\":null,\"step\":\"callback\",\"remaining_time\":null}],\"session_key\":null,\"synthesizer_name\":null}"))
		       .andExpect(jsonPath("processes[1].step").value(SYNTH_JOB))
		       .andExpect(jsonPath("processes[1].processSteps[0]").value(ANON_JOB))
		       .andExpect(jsonPath("processes[1].processSteps[1]").value(SYNTH_JOB))
		       .andExpect(jsonPath("processes[1].processSteps[2]").doesNotExist());
	}

	private void getResultFile() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/api/project/resultFile")
		                                      .param("executionStepName", "execution")
		                                      .param("processStepName", "anonymization")
		                                      .param("name", "additional.txt"))
		       .andExpect(status().isOk())
		       .andExpect(content().string("info"));
	}

	private void getResultZip() throws Exception {
		var result = mockMvc.perform(MockMvcRequestBuilders.get("/api/project/zip")
		                                                   .param("executionStepName", "EVALUATION")
		                                                   .param("processStepName", "anonymization")
		                                                   .param("name", "additional.txt"))
		                    .andExpect(status().isOk())
		                    .andExpect(header().exists("Content-Disposition"))
		                    .andExpect(header().string("Content-Disposition", "attachment; filename=process.zip"))
		                    .andExpect(content().contentType("application/zip"))
		                    .andReturn();

		final var expectedEntries = new java.util.HashMap<>(Map.ofEntries(
				Map.entry("attribute_config.yaml",
				          MutablePair.of(false, DataConfigurationTestHelper.generateDataConfigurationAsYaml())),
				Map.entry("original.csv", MutablePair.of(false, ResourceHelper.loadCsvFileAsString())),
				Map.entry("anonymization.yaml", MutablePair.of(false, "configuration")),
				Map.entry("anonymization.csv", MutablePair.of(false, ResourceHelper.loadCsvFileWithErrorsAsString()
				                                                                   .replace("forty two", ""))),
				Map.entry("additional.txt", MutablePair.of(false, "info")),
				Map.entry("synthetization_configuration.yaml", MutablePair.of(false, "configuration")),
				Map.entry("anonymization-synthetization.csv",
				          MutablePair.of(false, ResourceHelper.loadCsvFileAsString())),
				Map.entry("additional_1.txt", MutablePair.of(false, "info"))
		));
		final List<String> unexpectedEntries = new ArrayList<>();

		try (final var zipIn = new ZipInputStream(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {

			ZipEntry entry;
			while ((entry = zipIn.getNextEntry()) != null) {
				if (expectedEntries.containsKey(entry.getName())) {
					var expectedEntry = expectedEntries.get(entry.getName());
					expectedEntry.setLeft(true);

					final StringBuilder sb = new StringBuilder();
					final byte[] buffer = new byte[1024];
					int read;

					while((read = zipIn.read(buffer, 0, 1024)) >= 0) {
						sb.append(new String(buffer, 0, read));
					}
					assertEquals(expectedEntry.getRight(), sb.toString(), "Unexpected content of file: " + entry.getName());
				} else {
					unexpectedEntries.add(entry.getName());
				}

				zipIn.closeEntry();
			}
		}

		assertEquals(0, unexpectedEntries.size(), "Unexpected entries in the zip: " + unexpectedEntries);
		for (final var entry : expectedEntries.entrySet()) {
			assertTrue(entry.getValue().getLeft(), "Expected entry not found in the zip: " + entry.getKey());
		}
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
		       .andExpect(jsonPath("currentProcessIndex").value(0));

		mockMvc.perform(multipart("/api/process/execution/cancel")
				                .param("jobName", SYNTH_JOB))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("status").value(ProcessStatus.CANCELED.name()))
		       .andExpect(jsonPath("currentProcessIndex").value(nullValue()));

		// Test state changes
		final ExternalProcessEntity process = getTestProject().getPipelines().get(0).getStageByIndex(0).getProcess(0);
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
		mockMvc.perform(post("/api/config")
				                .with(httpBasic("test_user_3", "changeme"))
				                .param("configurationName", ANON_JOB)
				                .param("url", "/start_synthetization_process/ctgan")
				                .param("configuration", "configuration")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isOk());
		mockMvc.perform(post("/api/process/execution/configure")
				                .with(httpBasic("test_user_3", "changeme"))
				                .param("jobName", ANON_JOB)
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isOk());

		mockMvc.perform(post("/api/config")
				                .with(httpBasic("test_user_3", "changeme"))
				                .param("configurationName", "synthetization_configuration")
				                .param("url", "/start_synthetization_process/ctgan")
				                .param("configuration", "configuration")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isOk());
		mockMvc.perform(post("/api/process/execution/configure")
				                .with(httpBasic("test_user_3", "changeme"))
				                .param("jobName", SYNTH_JOB)
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isOk());

		mockMvc.perform(post("/api/process/execution/start")
				                .with(httpBasic("test_user_3", "changeme")))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("status").value(ProcessStatus.RUNNING.name()))
		       .andExpect(jsonPath("processes[0].externalProcessStatus").value(ProcessStatus.SCHEDULED.name()));

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
				                .param("jobName", SYNTH_JOB))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("status").value(ProcessStatus.CANCELED.name()));

		project = projectService.getProject(user);

		// Check if the second process is running
		assertEquals(ProcessStatus.RUNNING,
		             project.getPipelines().get(0).getStageByIndex(0).getProcess(0).getExternalProcessStatus());
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
		var process = updateTestProject.getPipelines().get(0).getStageByIndex(0).getProcess(0);
		String id = process.getUuid().toString();


		// Send callback request with error
		ErrorRequest errorResponse = new ErrorRequest("about:blank", "SYNTH_1_2_3", "An error occurred!", "An error occurred!");
		var errorJson = JsonMapper.jsonMapper().writeValueAsString(errorResponse);
		final MockMultipartFile resultData = new MockMultipartFile("error", "exception_message.txt",
		                                                           MediaType.APPLICATION_JSON_VALUE,
		                                                           errorJson.getBytes());
		mockMvc.perform(multipart("/api/process/" + id + "/callback")
				                .file(resultData))
				                .andExpect(status().isOk());
		var recordedRequest = mockBackEnd.takeRequest();
		assertEquals("POST", recordedRequest.getMethod());
		assertEquals("/start_synthetization_process/ctgan", recordedRequest.getPath());

		// Test state changes
		process = updateTestProject.getPipelines().get(0).getStageByIndex(0).getProcess(0);
		assertEquals(ProcessStatus.ERROR, process.getExecutionStep().getStatus());
		assertEquals(ProcessStatus.ERROR, process.getExternalProcessStatus(),
		             "External process status has not been updated!");
		assertEquals("An error occurred!", process.getStatus());
		assertFalse(process.getResultFiles().containsKey("exception_message.txt"),
		           "Exception message should not have been set!");
	}

	@Test
	public void startNoData() throws Exception {
		configure();
		mockMvc.perform(post("/api/process/execution/start"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage(
				       "The project '" + testProject.getId() + "' does not contain an original data set!"));
	}

	@Test
	public void startNoConfiguration() throws Exception {
		mockMvc.perform(post("/api/process/execution/start"))
		       .andExpect(status().isInternalServerError())
		       .andExpect(errorMessage(
				       "No configuration for step 'anonymization' found!"));
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
				       "Failed to start the process! Got status of '404 NOT_FOUND'. Got message: 'Not found'. Got error: 'Nicht gefunden, but in German'."));
	}

	@Test
	public void cancelNotStarted() throws Exception {
		mockMvc.perform(post("/api/process/execution/cancel"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("status").value(ProcessStatus.NOT_STARTED.name()));
	}

	@Test
	public void finishInvalidProcess() throws Exception {
		final String id = UUID.randomUUID().toString();
		final MockMultipartFile result = new MockMultipartFile("synthetic_data", "result.csv",
		                                                       MediaType.TEXT_PLAIN_VALUE,
		                                                       "result".getBytes());
		mockMvc.perform(multipart("/api/process/" + id + "/callback")
				                .file(result))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("No process with the given ID '" + id + "' exists!"));
	}

}
