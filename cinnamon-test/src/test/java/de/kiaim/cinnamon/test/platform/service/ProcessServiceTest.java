package de.kiaim.cinnamon.test.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.dto.ExternalProcessResponse;
import de.kiaim.cinnamon.platform.config.SerializationConfig;
import de.kiaim.cinnamon.platform.exception.BadStateException;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.configuration.Stage;
import de.kiaim.cinnamon.platform.model.entity.*;
import de.kiaim.cinnamon.platform.repository.ProjectRepository;
import de.kiaim.cinnamon.platform.service.*;
import de.kiaim.cinnamon.platform.model.enumeration.ProcessStatus;
import de.kiaim.cinnamon.platform.processor.CsvProcessor;
import de.kiaim.cinnamon.platform.repository.BackgroundProcessRepository;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import de.kiaim.cinnamon.test.util.WithMockWebServer;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@WithMockWebServer
public class ProcessServiceTest extends ContextRequiredTest {

	@Value("${server.port}") private int port;
	@Autowired private SerializationConfig serializationConfig;
	@Autowired private CinnamonConfiguration cinnamonConfiguration;
	@Autowired private DataProcessorService dataProcessorService;
	@Autowired private DataSetService dataSetService;
	@Autowired private HttpService httpService;
	@Autowired private StepService stepService = mock(StepService.class);

	private ObjectMapper jsonMapper = null;
	private MockWebServer mockBackEnd;

	private BackgroundProcessRepository backgroundProcessRepository;

	private ProcessService processService;

	@BeforeEach
	void setUpMockWebServer() {
		backgroundProcessRepository = mock(BackgroundProcessRepository.class);

		CsvProcessor csvProcessor = mock(CsvProcessor.class);
		DatabaseService databaseService = mock(DatabaseService.class);
		ExternalServerInstanceService externalServerInstanceService = mock(ExternalServerInstanceService.class);
		ProjectRepository projectRepository = mock(ProjectRepository.class);

		cinnamonConfiguration.getExternalServer()
		                     .get("anonymization-server")
		                     .setInstanceHostPort(mockBackEnd.getPort());
		this.processService = new ProcessService(serializationConfig, port, cinnamonConfiguration,
		                                         backgroundProcessRepository, projectRepository, csvProcessor,
		                                         databaseService, dataProcessorService, dataSetService,
		                                         externalServerInstanceService, httpService, stepService);

		if (jsonMapper == null) {
			jsonMapper = serializationConfig.jsonMapper();
		}
	}

	@Test
	public void fetchStatusError() throws IOException {
		final Stage stage = cinnamonConfiguration.getPipeline().getStageList().get(0);
		final ProjectEntity project = createProject(stage, ProcessStatus.RUNNING);

		final ExternalProcessResponse response = new ExternalProcessResponse();
		response.setError("An error occurred!");
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(500)
				                    .body(jsonMapper.writeValueAsString(response))
				                    .build());

		var updatedExecutionStep = assertDoesNotThrow(() -> processService.getStatus(project, stage));

		assertEquals(ProcessStatus.ERROR, updatedExecutionStep.getStatus(), "Status should be ERROR");
		assertEquals(
				"Failed to fetch the status! Got status of '500 INTERNAL_SERVER_ERROR'. Got error: 'An error occurred!'.",
				updatedExecutionStep.getProcess(0).getStatus());
	}

	@Test
	public void fetchStatusUnavailable() throws IOException {
		final Stage stage = cinnamonConfiguration.getPipeline().getStageList().get(0);
		final ProjectEntity project = createProject(stage, ProcessStatus.RUNNING);

		final ExternalProcessResponse response = new ExternalProcessResponse();
		response.setError("An error occurred!");
		mockBackEnd.shutdown();

		final var updatedExecutionStep = assertDoesNotThrow(() -> processService.getStatus(project, stage));

		assertEquals(ProcessStatus.ERROR, updatedExecutionStep.getStatus(), "Status should be ERROR");

		// Got different error messages on different machines, so only checking a part of it
		var message = updatedExecutionStep.getProcess(0).getStatus();
		assertNotNull(message, "Status message should not be null!");
		assertTrue(message.startsWith("Failed to fetch the status!"),
		           "Unexpected start of the error message: '" + message + "'");
		assertEquals("localhost/127.0.0.1:" + mockBackEnd.getPort(),
		             message.substring(message.lastIndexOf("localhost/")),
		             "Unexpected end of the error message: '" + message + "'");
	}

	@Test
	public void cancelProcessRunning() {
		final Stage stage = cinnamonConfiguration.getPipeline().getStageList().get(0);
		final ProjectEntity project = createProject(stage, ProcessStatus.RUNNING);

		final ExecutionStepEntity executionStep = project.getPipelines().get(0).getStageByStep(stage);
		final BackgroundProcessEntity process = executionStep.getProcess(0);

		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .build());

		assertDoesNotThrow(() -> processService.cancelProcess(process));

		// Test status of the process
		assertNull(process.getServerInstance(), "Server instance should be null!");
		assertNull(process.getScheduledTime(), "Scheduled time should be null!");
		assertEquals(ProcessStatus.CANCELED, process.getExternalProcessStatus(), "Status should be CANCELED!");

		// Test application state
		verify(backgroundProcessRepository, times(1))
				.findByEndpointInAndExternalProcessStatusOrderByScheduledTimeAsc(any(), any());
	}

	@Test
	public void cancelProcessScheduled() {
		final Stage stage = cinnamonConfiguration.getPipeline().getStageList().get(0);
		final ProjectEntity project = createProject(stage, ProcessStatus.SCHEDULED);

		final ExecutionStepEntity executionStep = project.getPipelines().get(0).getStageByStep(stage);
		final BackgroundProcessEntity process = executionStep.getProcess(0);

		assertDoesNotThrow(() -> processService.cancelProcess(process));

		// Test status of the process
		assertNull(process.getServerInstance(), "Server instance should be null!");
		assertNull(process.getScheduledTime(), "Scheduled time should be null!");
		assertEquals(ProcessStatus.CANCELED, process.getExternalProcessStatus(), "Status should be CANCELED!");

		// Test application state
		verify(backgroundProcessRepository, never())
				.findByEndpointInAndExternalProcessStatusOrderByScheduledTimeAsc(any(), any());
	}

	@Test
	public void deleteStage() {
		final Stage stage = cinnamonConfiguration.getPipeline().getStageList().get(0);
		final ProjectEntity project = createProject(stage, ProcessStatus.FINISHED);

		ExecutionStepEntity executionStep = assertDoesNotThrow(() -> processService.deleteStage(project, stage));

		assertEquals(ProcessStatus.NOT_STARTED, executionStep.getStatus(), "Status should be NOT_STARTED");

		ExternalProcessEntity externalProcess = executionStep.getProcess(0);
		assertEquals(ProcessStatus.NOT_STARTED, externalProcess.getExternalProcessStatus(),
		             "Status should be NOT_STARTED");
		assertTrue(externalProcess.getResultFiles().isEmpty(), "Result files should be empty!");
		assertNull(externalProcess.getStatus(), "Status should be null!");
	}

	@Test
	public void deleteStageRunning() {
		final Stage stage = cinnamonConfiguration.getPipeline().getStageList().get(0);
		final ProjectEntity project = createProject(stage, ProcessStatus.RUNNING);

		BadStateException exception = assertThrows(BadStateException.class,
		                                           () -> processService.deleteStage(project, stage));
		assertEquals("PLATFORM_1_8_1", exception.getErrorCode(), "Unexpected error code!");
	}

	private ProjectEntity createProject(final Stage stage, final ProcessStatus status) {
		final ExternalProcessEntity externalProcess = new DataProcessingEntity();
		externalProcess.setExternalProcessStatus(status);
		externalProcess.setJob(stage.getJobList().get(0));
		externalProcess.setUuid(UUID.randomUUID());

		final ExecutionStepEntity executionStep = new ExecutionStepEntity();
		executionStep.setStatus(status);
		executionStep.addProcess(externalProcess);

		final ProjectEntity project = new ProjectEntity();
		final PipelineEntity pipeline = project.addPipeline(new PipelineEntity());
		pipeline.addStage(stage, executionStep);

		if (status == ProcessStatus.SCHEDULED) {
			externalProcess.setScheduledTime(Timestamp.valueOf(LocalDateTime.of(2023, 11, 20, 12, 0)));
		} else if (status == ProcessStatus.RUNNING) {
			executionStep.setCurrentProcessIndex(0);
			externalProcess.setServerInstance("anonymization-server.0");
		} else if (status == ProcessStatus.FINISHED) {
			externalProcess.setStatus("FINISHED");
			externalProcess.getResultFiles().put("data", new LobWrapperEntity());
		}

		return project;
	}

}
