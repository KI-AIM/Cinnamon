package de.kiaim.test.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.dto.ExternalProcessResponse;
import de.kiaim.platform.config.SerializationConfig;
import de.kiaim.platform.exception.InternalRequestException;
import de.kiaim.platform.model.entity.ExecutionStepEntity;
import de.kiaim.platform.model.entity.ExternalProcessEntity;
import de.kiaim.platform.model.entity.PipelineEntity;
import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.processor.CsvProcessor;
import de.kiaim.platform.repository.ExternalProcessRepository;
import de.kiaim.platform.repository.ProjectRepository;
import de.kiaim.platform.service.DatabaseService;
import de.kiaim.platform.service.ProcessService;
import de.kiaim.platform.service.StepService;
import de.kiaim.test.platform.ContextRequiredTest;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.util.TestSocketUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class ProcessServiceTest extends ContextRequiredTest {

	private static final int mockBackEndPort = TestSocketUtils.findAvailableTcpPort();

	@Value("${server.port}") private int port;
	@Autowired private SerializationConfig serializationConfig;
	@Autowired private StepService stepService = mock(StepService.class);

	private ObjectMapper jsonMapper = null;
	private MockWebServer mockBackEnd;

	private ProcessService processService;

	@DynamicPropertySource
	static void dynamicProperties(DynamicPropertyRegistry registry) {
		registry.add("ki-aim.steps.synthetization.url", () -> String.format("http://localhost:%s", mockBackEndPort));
		registry.add("ki-aim.steps.anonymization.url", () -> String.format("http://localhost:%s", mockBackEndPort));
	}

	@BeforeEach
	void setUpMockWebServer() throws IOException {
		ProjectRepository projectRepository = mock(ProjectRepository.class);
		ExternalProcessRepository externalProcessRepository = mock(ExternalProcessRepository.class);

		CsvProcessor csvProcessor = mock(CsvProcessor.class);
		DatabaseService databaseService = mock(DatabaseService.class);

		this.processService = new ProcessService(serializationConfig, port, externalProcessRepository,
		                                         projectRepository, csvProcessor, databaseService, stepService);

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

		final Step curretnStep = Step.ANONYMIZATION;

		final ExternalProcessEntity externalProcess = new ExternalProcessEntity();
		externalProcess.setExternalProcessStatus(ProcessStatus.RUNNING);
		ReflectionTestUtils.setField(externalProcess, "id", 1L);

		final ExecutionStepEntity executionStep = new ExecutionStepEntity();
		executionStep.setCurrentStep(curretnStep);
		executionStep.setStatus(ProcessStatus.RUNNING);
		executionStep.putExternalProcess(curretnStep, externalProcess);

		final ProjectEntity project = new ProjectEntity();
		final PipelineEntity pipeline = project.addPipeline(new PipelineEntity());
		final Step step = Step.EXECUTION;
		pipeline.addStage(step, executionStep);

		final ExternalProcessResponse response = new ExternalProcessResponse();
		response.setError("An error occurred!");
		mockBackEnd.enqueue(new MockResponse.Builder()

				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(500)
				                    .body(jsonMapper.writeValueAsString(response))
				                    .build());

		final var exception = assertThrows(InternalRequestException.class, () -> {
			processService.getStatus(project, step);
		});

		assertEquals("Failed to fetch the status! Got status of 500 INTERNAL_SERVER_ERROR with message: 'null'",
		             exception.getMessage());
	}

	@Test
	public void fetchStatusUnavailable() throws IOException {
		final Step curretnStep = Step.ANONYMIZATION;

		final ExternalProcessEntity externalProcess = new ExternalProcessEntity();
		externalProcess.setExternalProcessStatus(ProcessStatus.RUNNING);
		ReflectionTestUtils.setField(externalProcess, "id", 1L);

		final ExecutionStepEntity executionStep = new ExecutionStepEntity();
		executionStep.setCurrentStep(curretnStep);
		executionStep.setStatus(ProcessStatus.RUNNING);
		executionStep.putExternalProcess(curretnStep, externalProcess);

		final ProjectEntity project = new ProjectEntity();
		final PipelineEntity pipeline = project.addPipeline(new PipelineEntity());
		final Step step = Step.EXECUTION;
		pipeline.addStage(step, executionStep);

		final ExternalProcessResponse response = new ExternalProcessResponse();
		response.setError("An error occurred!");
		mockBackEnd.shutdown();

		final var exception = assertThrows(InternalRequestException.class, () -> {
			processService.getStatus(project, step);
		});

		// Got different error messages on different machines, so only checking a part of it
		assertTrue(exception.getMessage().startsWith("Failed to fetch the status! Connection refused:"));
		assertTrue(exception.getMessage().endsWith("localhost/127.0.0.1:" + mockBackEndPort));
	}

}
