package de.kiaim.cinnamon.test.platform.service;

import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.enumeration.ProcessStatus;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.dto.ProjectExportParameter;
import de.kiaim.cinnamon.platform.model.entity.*;
import de.kiaim.cinnamon.platform.model.TransformationResult;
import de.kiaim.cinnamon.platform.model.enumeration.HoldOutSelector;
import de.kiaim.cinnamon.platform.model.enumeration.Step;
import de.kiaim.cinnamon.platform.model.file.FileType;
import de.kiaim.cinnamon.platform.processor.DataProcessor;
import de.kiaim.cinnamon.platform.repository.UserRepository;
import de.kiaim.cinnamon.platform.service.DataProcessorService;
import de.kiaim.cinnamon.platform.service.DatabaseService;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.platform.service.UserService;
import de.kiaim.cinnamon.test.platform.DatabaseTest;
import de.kiaim.cinnamon.test.util.DataConfigurationTestHelper;
import de.kiaim.cinnamon.test.util.FileConfigurationTestHelper;
import de.kiaim.cinnamon.test.util.ResourceHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
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
		ProjectEntity initialProject = new ProjectEntity();
		initialProject.getStatus().setCurrentStep(Step.VALIDATION);
		user.setProject(initialProject);
		initialProject = userRepository.save(user).getProject();

		final ProjectEntity project = projectService.getProject(user);

		assertEquals(initialProject.getId(), project.getId(), "The returned project is not equal to the users project!");
		assertEquals(project.getStatus().getCurrentStep(), Step.VALIDATION, "The initial status is wrong!");
	}

	@Test
	public void createZipFile() throws IOException, InternalDataSetPersistenceException, InternalMissingHandlingException, BadDataConfigurationException, BadStateException, BadDataSetIdException, InternalApplicationConfigurationException, BadConfigurationNameException, InternalIOException {
		// Preparation
		final var project = projectService.createProject(System.currentTimeMillis());
		final var stage = cinnamonConfiguration.getPipeline().getStageList().get(0);
		final var file = ResourceHelper.loadCsvFile();
		final var csvFileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(FileType.CSV, true);
		final var fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration();
		final DataConfiguration configuration = DataConfigurationTestHelper.generateDataConfiguration();

		final DataProcessor dataProcessor = dataProcessorService.getDataProcessor(csvFileConfiguration.getFileType());
		final TransformationResult transformationResult = assertDoesNotThrow(
				() -> dataProcessor.read(file.getInputStream(), csvFileConfiguration, configuration));
		assertDoesNotThrow(() -> databaseService.storeFile(project, file, fileConfiguration));
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

		execution = projectService.saveProject(project).getPipelines().get(0).getStages().get(0);

		var otherFile = ResourceHelper.loadCsvFileWithErrors();
		final TransformationResult otherTransformationResult = assertDoesNotThrow(
				() -> dataProcessor.read(otherFile.getInputStream(), csvFileConfiguration, configuration));
		databaseService.storeTransformationResult(otherTransformationResult,
		                                          (DataProcessingEntity) execution.getProcesses().get(0),
		                                          List.of(stage.getJobList().get(0)));

		// The test
		var out = new ByteArrayOutputStream();
		var parameter = new ProjectExportParameter(false, FileType.CSV, HoldOutSelector.ALL,
		                                           List.of("pipeline.execution.anonymization.dataset",
		                                                   "configuration.configurations", "original.dataset",
		                                                   "configuration.anonymization"));
		assertDoesNotThrow(() -> projectService.createZipFile(project, out, parameter));

		List<String> expectedFiles = new ArrayList<>(List.of("anonymization-dataset.csv", "original-attribute_config.yaml", "original-dataset.csv", "anonymization.yaml"));

		try (final var zipInputStream = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {

			var buffer = new byte[1024];
			int read = 0;
			ZipEntry zipEntry;

			while ((zipEntry = zipInputStream.getNextEntry()) != null) {
				var stringBuilder = new StringBuilder();
				while((read = zipInputStream.read(buffer, 0 , buffer.length)) > 0) {
					stringBuilder.append(new String(buffer, 0 , read));
				}

				if (zipEntry.getName().equals("anonymization-dataset.csv")) {
					var result = ResourceHelper.loadCsvFileWithErrorsAsString();
					var resultBuilder = new StringBuilder(result);
					resultBuilder.delete(result.length() - 24, result.length() - 15);

					assertEquals(resultBuilder.toString(), stringBuilder.toString(), "Unexpected anonymized data!");
				} else if (zipEntry.getName().equals("original-attribute_config.yaml")) {
					assertEquals(DataConfigurationTestHelper.generateDataConfigurationAsYaml(), stringBuilder.toString(), "Unexpected data configuration!");
				} else if(zipEntry.getName().equals("original-dataset.csv")) {
					assertEquals(ResourceHelper.loadCsvFileAsString(), stringBuilder.toString(), "Unexpected original data!");
				} else if(zipEntry.getName().equals("anonymization.yaml")) {
					assertEquals("key = value", stringBuilder.toString(), "Unexpected anonymization configuration!");
				} else {
					fail("Unexpected ZIP entry: " + zipEntry.getName());
				}

				expectedFiles.remove(zipEntry.getName());
			}
		}

		if (!expectedFiles.isEmpty()) {
			fail("The following files have not been found in the ZIP file: " + String.join(", ", expectedFiles));
		}
	}

}
