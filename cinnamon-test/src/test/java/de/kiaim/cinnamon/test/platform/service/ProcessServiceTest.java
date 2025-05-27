package de.kiaim.cinnamon.test.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.dto.ExternalProcessResponse;
import de.kiaim.cinnamon.platform.config.SerializationConfig;
import de.kiaim.cinnamon.platform.exception.InternalRequestException;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.configuration.Stage;
import de.kiaim.cinnamon.platform.model.entity.*;
import de.kiaim.cinnamon.platform.service.*;
import de.kiaim.cinnamon.platform.model.enumeration.ProcessStatus;
import de.kiaim.cinnamon.platform.processor.CsvProcessor;
import de.kiaim.cinnamon.platform.repository.BackgroundProcessRepository;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class ProcessServiceTest extends ContextRequiredTest {

	private static final int mockBackEndPort = TestSocketUtils.findAvailableTcpPort();

	@Value("${server.port}") private int port;
	@Autowired private SerializationConfig serializationConfig;
	@Autowired private CinnamonConfiguration cinnamonConfiguration;
	@Autowired private DataSetService dataSetService;
	@Autowired private HttpService httpService;
	@Autowired private StepService stepService = mock(StepService.class);

	private ObjectMapper jsonMapper = null;
	private MockWebServer mockBackEnd;

	private ProcessService processService;

	@DynamicPropertySource
	static void dynamicProperties(DynamicPropertyRegistry registry) {
		registry.add("cinnamon.external-server.technical-evaluation-server.urlServer", () -> String.format("http://localhost:%s", mockBackEndPort));
		registry.add("cinnamon.external-server.synthetization-server.urlServer", () -> String.format("http://localhost:%s", mockBackEndPort));
		registry.add("cinnamon.external-server.anonymization-server.urlServer", () -> String.format("http://localhost:%s", mockBackEndPort));
	}

	@BeforeEach
	void setUpMockWebServer() throws IOException {
		BackgroundProcessRepository backgroundProcessRepository = mock(BackgroundProcessRepository.class);

		CsvProcessor csvProcessor = mock(CsvProcessor.class);
		DatabaseService databaseService = mock(DatabaseService.class);
		ProjectService projectService = mock(ProjectService.class);

		this.processService = new ProcessService(serializationConfig, port, cinnamonConfiguration,
		                                         backgroundProcessRepository, csvProcessor, databaseService,
		                                         dataSetService, httpService, projectService, stepService);

		mockBackEnd = new MockWebServer();
		mockBackEnd.start(mockBackEndPort);

		if (jsonMapper == null) {
			jsonMapper = serializationConfig.jsonMapper();
		}
	}

	@AfterEach
	void shutDownMockWebServer() throws IOException {
		mockBackEnd.shutdown();
	}

	@Test
	public void fetchStatusError() throws IOException {
		final Stage stage = cinnamonConfiguration.getPipeline().getStageList().get(0);

		final ExternalProcessEntity externalProcess = new DataProcessingEntity();
		externalProcess.setExternalProcessStatus(ProcessStatus.RUNNING);
		externalProcess.setJob(stage.getJobList().get(0));
		externalProcess.setUuid(UUID.randomUUID());

		final ExecutionStepEntity executionStep = new ExecutionStepEntity();
		executionStep.setCurrentProcessIndex(0);
		executionStep.setStatus(ProcessStatus.RUNNING);
		executionStep.addProcess(externalProcess);

		final ProjectEntity project = new ProjectEntity();
		final PipelineEntity pipeline = project.addPipeline(new PipelineEntity());
		pipeline.addStage(stage, executionStep);

		final ExternalProcessResponse response = new ExternalProcessResponse();
		response.setError("An error occurred!");
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(500)
				                    .body(jsonMapper.writeValueAsString(response))
				                    .build());

		var updatedExecutionStep = processService.getStatus(project, stage);

		assertEquals(ProcessStatus.ERROR, updatedExecutionStep.getStatus(), "Status should be ERROR");
		assertEquals(
				"Failed to fetch the status! Got status of '500 INTERNAL_SERVER_ERROR'. Got error: 'An error occurred!'.",
				updatedExecutionStep.getProcess(0).getStatus());
	}

	@Test
	public void fetchStatusUnavailable() throws IOException {
		final Stage stage = cinnamonConfiguration.getPipeline().getStageList().get(0);

		final ExternalProcessEntity externalProcess = new DataProcessingEntity();
		externalProcess.setExternalProcessStatus(ProcessStatus.RUNNING);
		externalProcess.setJob(stage.getJobList().get(0));
		externalProcess.setUuid(UUID.randomUUID());

		final ExecutionStepEntity executionStep = new ExecutionStepEntity();
		executionStep.setCurrentProcessIndex(0);
		executionStep.setStatus(ProcessStatus.RUNNING);
		executionStep.addProcess(externalProcess);

		final ProjectEntity project = new ProjectEntity();
		final PipelineEntity pipeline = project.addPipeline(new PipelineEntity());
		pipeline.addStage(stage, executionStep);

		final ExternalProcessResponse response = new ExternalProcessResponse();
		response.setError("An error occurred!");
		mockBackEnd.shutdown();

		final var updatedExecutionStep = processService.getStatus(project, stage);

		assertEquals(ProcessStatus.ERROR, updatedExecutionStep.getStatus(), "Status should be ERROR");

		// Got different error messages on different machines, so only checking a part of it
		var message = updatedExecutionStep.getProcess(0).getStatus();
		assertNotNull(message, "Status message should not be null!");
		assertTrue(message.startsWith("Failed to fetch the status!"), "Unexpected error message: '" + message + "'");
		assertTrue(message.contains("Connection refused:"), "Unexpected error message: '" + message + "'");
		assertTrue(message.endsWith("localhost/127.0.0.1:" + mockBackEndPort),"Unexpected error message: " + message + "'");
	}

}
