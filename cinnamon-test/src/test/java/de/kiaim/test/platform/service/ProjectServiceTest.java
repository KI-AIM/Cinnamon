package de.kiaim.test.platform.service;

import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.platform.exception.*;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.entity.*;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.model.file.FileType;
import de.kiaim.platform.processor.DataProcessor;
import de.kiaim.platform.repository.UserRepository;
import de.kiaim.platform.service.DataProcessorService;
import de.kiaim.platform.service.DatabaseService;
import de.kiaim.platform.service.ProjectService;
import de.kiaim.platform.service.UserService;
import de.kiaim.test.platform.DatabaseTest;
import de.kiaim.test.util.DataConfigurationTestHelper;
import de.kiaim.test.util.FileConfigurationTestHelper;
import de.kiaim.test.util.ResourceHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectServiceTest extends DatabaseTest {

	@Autowired CinnamonConfiguration cinnamonConfiguration;
	@Autowired UserRepository userRepository;
	@Autowired DatabaseService databaseService;
	@Autowired DataProcessorService dataProcessorService;
	@Autowired ProjectService projectService;
	@Autowired UserService userService;

	@Test
	public void createProject() {
		var user = userService.save("email", "password");

		assertDoesNotThrow(() -> projectService.createProject(user));

		assertNotNull(user.getProject(), "No project has been created!");
		var project = user.getProject();

		assertEquals(1, project.getPipelines().size(), "Unexpected  number of created pipelines!");
		var pipeline = project.getPipelines().get(0);
		assertEquals(2, pipeline.getStages().size(), "Unexpected number of created executions!");
		assertTrue(pipeline.containsStage(cinnamonConfiguration.getPipeline().getStageList().get(0)),
		           "No execution has been created for step 'EVALUATION'!");
		assertTrue(pipeline.containsStage(cinnamonConfiguration.getPipeline().getStageList().get(1)),
		           "No execution has been created for step 'EXECUTION'!");
		var stage = cinnamonConfiguration.getPipeline().getStageList().get(0);
		var exec = pipeline.getStageByStep(stage);

		assertEquals(exec.getStage(), stage, "Unexpected step!");
		assertEquals(ProcessStatus.NOT_STARTED, exec.getStatus(), "Unexpected process status!");
		assertNull(exec.getCurrentProcessIndex(), "No step has been created!");
		assertEquals(2, exec.getProcesses().size(), "Unexpected number of processes!");
		var firstProcess = exec.getProcess(0);

		assertEquals(firstProcess.getExternalProcessStatus(), ProcessStatus.NOT_STARTED , "Unexpected status!");
	}

	@Test
	public void getExistingProject() {
		final UserEntity user = getTestUser();
		final ProjectEntity initialProject = new ProjectEntity();
		initialProject.getStatus().setCurrentStep(Step.VALIDATION);
		user.setProject(initialProject);
		userRepository.save(user);

		final ProjectEntity project = projectService.getProject(user);

		assertEquals(initialProject.getId(), project.getId(), "The returned project is not equal to the users project!");
		assertEquals(project.getStatus().getCurrentStep(), Step.VALIDATION, "The initial status is wrong!");
	}

	@Test
	public void createZipFile() throws IOException, InternalDataSetPersistenceException, InternalMissingHandlingException, BadDataConfigurationException, BadStateException, BadDataSetIdException, BadFileException, InternalApplicationConfigurationException, BadConfigurationNameException {
		// Preparation
		final var project = projectService.createProject(System.currentTimeMillis());
		final var stage = cinnamonConfiguration.getPipeline().getStageList().get(0);
		final var file = ResourceHelper.loadCsvFile();
		final var csvFileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(FileType.CSV, true);
		final var fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration();
		final DataConfiguration configuration = DataConfigurationTestHelper.generateDataConfiguration();

		final DataProcessor dataProcessor = dataProcessorService.getDataProcessor(csvFileConfiguration.getFileType());
		final TransformationResult transformationResult = dataProcessor.read(file.getInputStream(), csvFileConfiguration,
		                                                                     configuration);
		databaseService.storeFile(project, file, fileConfiguration);
		databaseService.storeOriginalTransformationResult(transformationResult, project);
		databaseService.storeConfiguration("anonymization", null, "key = value", project);

		var pipeline = new PipelineEntity();
		project.addPipeline(pipeline);

		var execution = new ExecutionStepEntity();
		pipeline.addStage(stage, execution);

		for (final var processStep : stage.getJobList()) {
			final var process = new DataProcessingEntity();
			process.setJob(processStep);
			execution.addProcess(process);
		}

		var otherFile = ResourceHelper.loadCsvFileWithErrors();
		final TransformationResult otherTransformationResult = dataProcessor.read(otherFile.getInputStream(),
		                                                                     csvFileConfiguration,
		                                                                     configuration);
		databaseService.storeTransformationResult(otherTransformationResult,
		                                          (DataProcessingEntity) execution.getProcesses().get(0),
		                                          List.of(stage.getJobList().get(0)));

		// The test
		var out = new ByteArrayOutputStream();
		assertDoesNotThrow(() -> projectService.createZipFile(project, out));

		try (final var zipInputStream = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {

			var buffer = new byte[1024];
			int read = 0;
			ZipEntry zipEntry;

			while ((zipEntry = zipInputStream.getNextEntry()) != null) {
				var stringBuilder = new StringBuilder();
				while((read = zipInputStream.read(buffer, 0 , buffer.length)) > 0) {
					stringBuilder.append(new String(buffer, 0 , read));
				}

				if (zipEntry.getName().equals("anonymization.csv")) {
					var result = ResourceHelper.loadCsvFileWithErrorsAsString();
					var resultBuilder = new StringBuilder(result);
					resultBuilder.delete(result.length() - 24, result.length() - 15);

					assertEquals(resultBuilder.toString(), stringBuilder.toString(), "Unexpected configuration!");
				} else if (zipEntry.getName().equals("attribute_config.yaml")) {
					assertEquals(DataConfigurationTestHelper.generateDataConfigurationAsYaml(), stringBuilder.toString(), "Unexpected data configuration!");
				} else if(zipEntry.getName().equals("original.csv")) {
					assertEquals(ResourceHelper.loadCsvFileAsString(), stringBuilder.toString(), "Unexpected configuration!");
				} else if(zipEntry.getName().equals("anonymization.yaml")) {
					assertEquals("key = value", stringBuilder.toString(), "Unexpected configuration!");
				} else {
					fail("Unexpected ZIP entry: " + zipEntry.getName());
				}
			}
		}

	}

}
