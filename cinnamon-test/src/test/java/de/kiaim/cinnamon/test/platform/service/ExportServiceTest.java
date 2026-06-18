package de.kiaim.cinnamon.test.platform.service;

import de.kiaim.cinnamon.model.configuration.data.DataSourceConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.model.enumeration.DataSourceType;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.TransformationResult;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.dto.ProjectExportParameter;
import de.kiaim.cinnamon.platform.model.entity.DataProcessingEntity;
import de.kiaim.cinnamon.platform.model.entity.ExecutionStepEntity;
import de.kiaim.cinnamon.platform.model.entity.PipelineEntity;
import de.kiaim.cinnamon.platform.model.enumeration.HoldOutSelector;
import de.kiaim.cinnamon.model.configuration.data.file.FileType;
import de.kiaim.cinnamon.platform.processor.DataProcessor;
import de.kiaim.cinnamon.platform.service.DataProcessorService;
import de.kiaim.cinnamon.platform.service.DatabaseService;
import de.kiaim.cinnamon.platform.service.ExportService;
import de.kiaim.cinnamon.test.platform.DatabaseTest;
import de.kiaim.cinnamon.test.util.*;
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
import static org.junit.jupiter.api.Assertions.fail;

public class ExportServiceTest extends DatabaseTest {

	@Autowired CinnamonConfiguration cinnamonConfiguration;
	@Autowired DatabaseService databaseService;
	@Autowired DataProcessorService dataProcessorService;
	@Autowired ExportService exportService;

	@Test
	public void createZipFile() throws IOException, InternalDataSetPersistenceException, InternalMissingHandlingException, BadDataConfigurationException, BadStateException, BadDataSetIdException, InternalApplicationConfigurationException, InternalIOException {
		// Preparation
		final var project = projectService.createProject(getTestUser());
		projectService.updateProjectConfiguration(project, ProjectConfigurationTestHelper.generateProjectConfigurationDTO());

		final var stage = cinnamonConfiguration.getPipeline().getStageList().get(0);
		final var dataSourceConfiguration = FileConfigurationTestHelper.generateDataSourceConfiguration(DataSourceType.LOCAL);
		final var file = ResourceHelper.loadCsvFile();
		final var csvFileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(FileType.CSV, true);
		final var fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration();
		final DataConfiguration configuration = DataConfigurationTestHelper.generateDataConfiguration();
		final String anonymizationConfiguration = AlgorithmTestHelper.generateAlgorithmConfigurationYaml();

		final DataProcessor dataProcessor = dataProcessorService.getDataProcessor(csvFileConfiguration.getFileType());
		assertDoesNotThrow(() -> databaseService.storeDataSourceConfiguration(project, dataSourceConfiguration));
		assertDoesNotThrow(() -> databaseService.storeFileConfiguration(project, fileConfiguration));
		assertDoesNotThrow(() -> databaseService.storeFile(project, file));
		assertDoesNotThrow(() -> databaseService.storeOriginalDataConfiguration(configuration, project));
		assertDoesNotThrow(() -> databaseService.storeOriginalDataset(project));
		assertDoesNotThrow(
				() -> databaseService.storeConfiguration("anonymization", anonymizationConfiguration, project));

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
		                                                   "configuration.project",
														   "configuration.dataSource",
														   "configuration.file",
														   "configuration.dataset",
		                                                   "configuration.pipeline",
		                                                   "configuration.configurations",
		                                                   "configuration.anonymization",
		                                                   "original.dataset",
		                                                   "original.file"
		                                           ));
		assertDoesNotThrow(() -> exportService.createZipFile(project, out, parameter));

		List<String> expectedFiles = new ArrayList<>(
				List.of("anonymization-dataset.csv", "configurations.yaml", "original-file-file.csv",
				        "original-dataset.csv", "anonymization.yaml", "project.yaml", "dataSource.yaml", "file.yaml",
				        "dataset.yaml", "pipeline.yaml"));

		try (final var zipInputStream = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {

			var buffer = new byte[1024];
			int read;
			ZipEntry zipEntry;

			while ((zipEntry = zipInputStream.getNextEntry()) != null) {
				var stringBuilder = new StringBuilder();
				while((read = zipInputStream.read(buffer, 0 , buffer.length)) > 0) {
					stringBuilder.append(new String(buffer, 0 , read));
				}

				switch (zipEntry.getName()) {
					case "anonymization-dataset.csv" -> {
						var result = ResourceHelper.loadCsvFileWithErrorsAsString();
						var resultBuilder = new StringBuilder(result);
						resultBuilder.delete(result.length() - 24, result.length() - 15);

						assertEquals(resultBuilder.toString(), stringBuilder.toString(), "Unexpected anonymized data!");
					}
					case "configurations.yaml" ->
							assertEquals(DataConfigurationTestHelper.generateDataConfigurationAsYaml(),
							             stringBuilder.toString(), "Unexpected data configuration!");
					case "original-dataset.csv" ->
							assertEquals(ResourceHelper.loadCsvFileAsString(),
							             ResourceHelper.unifyLineEndings(stringBuilder.toString()),
							             "Unexpected original data!");
					case "original-file-file.csv" ->
							assertEquals(ResourceHelper.loadCsvFileAsString(),
							             ResourceHelper.unifyLineEndings(stringBuilder.toString()),
							             "Unexpected original file!");
					case "anonymization.yaml" ->
							assertEquals(AlgorithmTestHelper.generateAlgorithmConfigurationYaml(),
							             stringBuilder.toString(),
							             "Unexpected anonymization configuration!");
					case "project.yaml" ->
							assertEquals(ProjectConfigurationTestHelper.generateProjectConfigurationAsExport(),
							             stringBuilder.toString(), "Unexpected project configuration!");
					case "dataSource.yaml" ->
							assertEquals(FileConfigurationTestHelper.generateDataSourceConfigurationAsYaml(),
							             stringBuilder.toString(), "Unexpected data source configuration!");
					case "file.yaml" ->
							assertEquals(FileConfigurationTestHelper.generateFileConfigurationAsYaml(),
							             stringBuilder.toString(), "Unexpected file configuration!");
					case "dataset.yaml" ->
							assertEquals(DataConfigurationTestHelper.generateDatasetConfigurationAsYaml(),
							             stringBuilder.toString(), "Unexpected dataset configuration!");
					case "pipeline.yaml" ->
							assertEquals(generatePipelineConfigurationAsYaml(), stringBuilder.toString(),
							             "Unexpected pipeline configuration!");
					default -> fail("Unexpected ZIP entry: " + zipEntry.getName());
				}

				expectedFiles.remove(zipEntry.getName());
			}
		}

		if (!expectedFiles.isEmpty()) {
			fail("The following files have not been found in the ZIP file: " + String.join(", ", expectedFiles));
		}
	}

	private String generatePipelineConfigurationAsYaml() {
		return """
		       pipeline:
		         pipelines:
		         - jobs:
		           - name: "anonymization"
		             enabled: true
		             configuration: 0
		           - name: "synthetization"
		             enabled: true
		             configuration: 0
		           - name: "technical_evaluation"
		             enabled: true
		             configuration: 0
		           - name: "risk_evaluation"
		             enabled: true
		             configuration: 0
		           - name: "risk_evaluation_o"
		             enabled: true
		             configuration: 0
		       """;
	}

}
