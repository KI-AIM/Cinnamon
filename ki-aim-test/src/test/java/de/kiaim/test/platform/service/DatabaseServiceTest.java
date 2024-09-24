package de.kiaim.test.platform.service;

import de.kiaim.model.configuration.data.ColumnConfiguration;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.model.data.DataRow;
import de.kiaim.model.data.DataSet;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.platform.exception.BadConfigurationNameException;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.entity.DataSetEntity;
import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.model.enumeration.Mode;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.service.DatabaseService;
import de.kiaim.platform.service.ProjectService;
import de.kiaim.test.platform.DatabaseTest;
import de.kiaim.test.util.TransformationResultTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseServiceTest extends DatabaseTest {

	@Autowired
	DatabaseService databaseService;

	@Autowired
	ProjectService projectService;

	@BeforeEach
	public void setUp() {
		projectService.setMode(testProject, Mode.EXPERT);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void storeAndDelete() {
		final TransformationResult transformationResult = TransformationResultTestHelper.generateTransformationResult(false);

		long dataSetId = assertDoesNotThrow(() -> databaseService.storeTransformationResult(transformationResult, testProject, Step.VALIDATION));

		assertTrue(testProject.getDataSets().containsKey(Step.VALIDATION), "Data set has not been created!");
		final DataSetEntity dataSetEntity = testProject.getDataSets().get(Step.VALIDATION);

		assertTrue(existsTable(dataSetId), "Table could not be found!");
		assertEquals(2, countEntries(dataSetId), "Number of entries wrong!");
		assertTrue(existsDataSet(dataSetId), "Configuration has not been persisted!");
		assertEquals(dataSetId, dataSetEntity.getId(), "Project has been associated with the wrong dataset!");
		assertTrue(dataSetEntity.isStoredData(), "Flag that the data is stored should be true!");
		assertEquals(0, dataTransformationErrorRepository.countByDataSetId((testProject).getId()),
		             "No transformation errors should have been persisted!");

		assertDoesNotThrow(() -> databaseService.delete(testProject));

		assertFalse(existsTable(dataSetId), "Table should be deleted!");
		assertFalse(dataSetEntity.isStoredData(), "Flag that the data is stored should be false!");

	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void storeAndDeleteWithErrors() {
		final TransformationResult transformationResult = TransformationResultTestHelper.generateTransformationResult(true);

		long dataSetId = assertDoesNotThrow(() -> databaseService.storeTransformationResult(transformationResult, testProject, Step.VALIDATION));
		final DataSetEntity dataSetEntity = testProject.getDataSets().get(Step.VALIDATION);

		assertTrue(existsTable(dataSetId), "Table could not be found!");
		assertEquals(3, countEntries(dataSetId), "Number of entries wrong!");
		assertTrue(existsDataSet(dataSetId), "Configuration has not been persisted!");
		assertNotNull(testProject, "User has not been associated with the dataset!");
		assertEquals(dataSetId, dataSetEntity.getId(), "User has been associated with the wrong dataset!");
		assertTrue(dataSetEntity.isStoredData(), "Flag that the data is stored should be true!");
		assertEquals(2, dataTransformationErrorRepository.countByDataSetId(dataSetId),
		             "Transformation errors have not been persisted!");

		assertDoesNotThrow(() -> databaseService.delete(testProject));

		assertFalse(existsTable(dataSetId), "Table should be deleted!");
		assertFalse(dataSetEntity.isStoredData(), "Flag that the data is stored should be false!");
		assertEquals(0, dataTransformationErrorRepository.countByDataSetId(dataSetId),
		             "Transformation errors have not been removed!");
	}

	@Test
	@Transactional
	void storeConfiguration() {
		final String configName = "testConfig";
		final String config = """
				configurations:
				- index: 0
				  name: "column0_boolean"
				  type: "BOOLEAN"
				  scale: "NOMINAL"
				  configurations: []
				""";

		final UserEntity user = getTestUser();
		final ProjectEntity project = projectService.getProject(user);

		assertDoesNotThrow(() -> databaseService.storeConfiguration(configName, config, project),
		                   "The configuration could not be stored!");

		final UserEntity updatedUser = getTestUser();

		final ProjectEntity updatedProject = updatedUser.getProject();
		assertNotNull(updatedProject, "The configuration has not been created!");
		assertTrue(updatedProject.getConfigurations().containsKey(configName),
		           "The configuration has not been stored correctly under the user!");
		assertEquals(config, updatedProject.getConfigurations().get(configName),
		             "The configuration has not been stored correctly!");
	}

	@Test
	@Transactional
	void storeConfigurationOverwrite() {
		final String configName = "testConfigName";
		final String config = "Test config";

		storeConfiguration(configName, config);

		final UserEntity user = getTestUser();
		final ProjectEntity project = projectService.getProject(user);
		final String updatedConfig = "Updated test config";
		assertDoesNotThrow(() -> databaseService.storeConfiguration(configName, updatedConfig, project),
		                   "The configuration could not be updated!");

		final UserEntity updatedUser = getTestUser();

		final ProjectEntity updatedProject = updatedUser.getProject();
		assertNotNull(updatedProject, "The configuration has not been created!");
		assertTrue(updatedProject.getConfigurations().containsKey(configName),
		           "The configuration has not been stored correctly under the user!");
		assertEquals(updatedConfig, updatedProject.getConfigurations().get(configName),
		             "The configuration has not been stored correctly!");
	}

	@Test
	void exportDataSet() {
		final TransformationResult transformationResult = TransformationResultTestHelper.generateTransformationResult(false);
		final UserEntity user = getTestUser();
		final ProjectEntity project = projectService.getProject(user);

		assertDoesNotThrow(() -> databaseService.storeTransformationResult(transformationResult, project, Step.VALIDATION));

		final DataSet export = assertDoesNotThrow(() -> databaseService.exportDataSet(project, new ArrayList<>(), Step.VALIDATION));
		assertEquals(transformationResult.getDataSet(), export, "Data sets do not match!");

		assertDoesNotThrow(() -> databaseService.delete(project));
	}

	@Test
	void exportDataSetColumns() {
		final TransformationResult transformationResult = TransformationResultTestHelper.generateTransformationResult(false);
		final UserEntity user = getTestUser();
		final ProjectEntity project = projectService.getProject(user);

		assertDoesNotThrow(() -> databaseService.storeTransformationResult(transformationResult, project, Step.VALIDATION));

		final DataSet export = assertDoesNotThrow(
				() -> databaseService.exportDataSet(project, List.of("column4_integer", "column0_boolean"), Step.VALIDATION));

		// Test the data configuration
		final DataConfiguration config = export.getDataConfiguration();
		assertEquals(2, config.getConfigurations().size(), "Number of columns does not match!");

		final ColumnConfiguration firstColumnConfig = config.getConfigurations().get(0);
		assertEquals(0, firstColumnConfig.getIndex(), "Index of first column does not match!");
		assertEquals("column4_integer", firstColumnConfig.getName(), "Name of first column does not match!");
		assertEquals(DataType.INTEGER, firstColumnConfig.getType(), "Type of first column does not match!");

		final ColumnConfiguration secondColumnConfig = config.getConfigurations().get(1);
		assertEquals(1, secondColumnConfig.getIndex(), "Index of second column does not match!");
		assertEquals("column0_boolean", secondColumnConfig.getName(), "Name of second column does not match!");
		assertEquals(DataType.BOOLEAN, secondColumnConfig.getType(), "Type of second column does not match!");

		// Test the data set
		assertEquals(2, export.getDataRows().size(), "Number of rows does not match!");

		final DataRow firstRow = export.getDataRows().get(0);
		assertEquals(2, firstRow.getData().size(), "Number of columns does not match!");
		assertEquals(DataType.INTEGER, firstRow.getData().get(0).getDataType(), "Type of first value does not match!");
		assertEquals(DataType.BOOLEAN, firstRow.getData().get(1).getDataType(), "Type of second value does not match!");

		assertDoesNotThrow(() -> databaseService.delete(project));
	}

	@Test
	@Transactional
	void exportConfiguration() {
		final String configName = "testConfigName";
		final String config = """
				configurations:
				- index: 0
				  name: "column0_boolean"
				  type: "BOOLEAN"
				  scale: "NOMINAL"
				  configurations: []
				""";

		storeConfiguration(configName, config);

		final UserEntity user = getTestUser();
		final ProjectEntity project = projectService.getProject(user);

		final String exportedConfig = assertDoesNotThrow(() -> databaseService.exportConfiguration(configName, project),
		                                                 "The configuration could not be exported!");
		assertEquals(config, exportedConfig, "The exported config does not match the original config!");
	}

	@Test
	@Transactional
	void exportConfigurationNoConfiguration() {
		final String configName = "testConfigName";
		final UserEntity user = getTestUser();
		final ProjectEntity project = projectService.getProject(user);
		assertThrows(BadConfigurationNameException.class, () -> databaseService.exportConfiguration(configName, project),
		             "Configuration should not be present!");
	}

	@Test
	@Transactional
	void exportConfigurationInvalidName() {
		final String configName = "testConfigName";
		final String invalidConfigName = "invalidConfigName";
		final String config = "Test config";
		final UserEntity user = getTestUser();
		final ProjectEntity project = projectService.getProject(user);

		storeConfiguration(configName, config);

		assertThrows(BadConfigurationNameException.class,
		             () -> databaseService.exportConfiguration(invalidConfigName, project),
		             "Configuration should not be present!");
	}

	@Test
	void countEntries() {
		final TransformationResult transformationResult = TransformationResultTestHelper.generateTransformationResult(false);
		final UserEntity user = getTestUser();
		final ProjectEntity project = projectService.getProject(user);

		assertDoesNotThrow(() -> databaseService.storeTransformationResult(transformationResult, project, Step.VALIDATION));
		final DataSetEntity dataSet = project.getDataSets().get(Step.VALIDATION);

		final int numberRows = assertDoesNotThrow(() -> databaseService.countEntries(dataSet.getId()));
		assertEquals(2, numberRows, "Number of entries does not match!");

		assertDoesNotThrow(() -> databaseService.delete(project));
	}

	@Test
	void countInvalidRows() {
		final TransformationResult transformationResult = TransformationResultTestHelper.generateTransformationResult(true);
		final UserEntity user = getTestUser();
		final ProjectEntity project = projectService.getProject(user);

		assertDoesNotThrow(() -> databaseService.storeTransformationResult(transformationResult, project, Step.VALIDATION));
		final DataSetEntity dataSet = project.getDataSets().get(Step.VALIDATION);

		final int numberInvalidRows = assertDoesNotThrow(() -> databaseService.countInvalidRows(dataSet.getId()));
		assertEquals(1, numberInvalidRows, "Number of invalid rows does not match!");

		assertDoesNotThrow(() -> databaseService.delete(project));
	}

	@Test
	void existsTableTest() {
		final TransformationResult transformationResult = TransformationResultTestHelper.generateTransformationResult(false);
		final UserEntity user = getTestUser();
		final ProjectEntity project = projectService.getProject(user);

		assertDoesNotThrow(() -> databaseService.storeTransformationResult(transformationResult, project, Step.VALIDATION));
		final DataSetEntity dataSet = project.getDataSets().get(Step.VALIDATION);

		final boolean exists = assertDoesNotThrow(() -> databaseService.existsTable(dataSet.getId()));
		assertTrue(exists, "Table does not exist!");

		assertDoesNotThrow(() -> databaseService.delete(project));
	}

	@Test
	void existsTableNot() {
		final boolean exists = assertDoesNotThrow(() -> databaseService.existsTable(0));
		assertFalse(exists, "Table does exist!");
	}
}
