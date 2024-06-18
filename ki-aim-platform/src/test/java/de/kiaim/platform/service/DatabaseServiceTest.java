package de.kiaim.platform.service;

import de.kiaim.model.configuration.ColumnConfiguration;
import de.kiaim.model.configuration.DataConfiguration;
import de.kiaim.model.data.DataRow;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.platform.DatabaseTest;
import de.kiaim.platform.TestModelHelper;
import de.kiaim.platform.exception.BadConfigurationNameException;
import de.kiaim.platform.exception.BadDataSetIdException;
import de.kiaim.platform.model.DataSet;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.entity.PlatformConfigurationEntity;
import de.kiaim.platform.model.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


class DatabaseServiceTest extends DatabaseTest {

	@Autowired
	DatabaseService databaseService;

	@Test
	void storeAndDelete() {
		final TransformationResult transformationResult = TestModelHelper.generateTransformationResult(false);
		final UserEntity user = getTestUser();

		long dataSetId = assertDoesNotThrow(() -> databaseService.store(transformationResult, user));

		UserEntity testUser = getTestUser();
		assertTrue(existsTable(dataSetId), "Table could not be found!");
		assertEquals(2, countEntries(dataSetId), "Number of entries wrong!");
		assertTrue(existsDataConfigration(dataSetId), "Configuration has not been persisted!");
		PlatformConfigurationEntity platformConfiguration = testUser.getPlatformConfiguration();
		assertNotNull(platformConfiguration, "User has not been associated with the dataset!");
		assertEquals(dataSetId, platformConfiguration.getId(), "User has been associated with the wrong dataset!");
		assertEquals(0, dataTransformationErrorRepository.countByPlatformConfigurationId(platformConfiguration.getId()),
		             "No transformation errors should have been persisted!");

		assertDoesNotThrow(() -> databaseService.delete(user));

		assertFalse(existsTable(dataSetId), "Table should be deleted!");
		assertFalse(existsDataConfigration(dataSetId), "Configuration has not been deleted!");
		assertNull(getTestUser().getPlatformConfiguration(), "User association with the dataset has not been removed!");
	}

	@Test
	void storeAndDeleteWithErrors() {
		final TransformationResult transformationResult = TestModelHelper.generateTransformationResult(true);
		final UserEntity user = getTestUser();

		long dataSetId = assertDoesNotThrow(() -> databaseService.store(transformationResult, user));

		UserEntity testUser = getTestUser();
		assertTrue(existsTable(dataSetId), "Table could not be found!");
		assertEquals(3, countEntries(dataSetId), "Number of entries wrong!");
		assertTrue(existsDataConfigration(dataSetId), "Configuration has not been persisted!");
		PlatformConfigurationEntity platformConfiguration = testUser.getPlatformConfiguration();
		assertNotNull(platformConfiguration, "User has not been associated with the dataset!");
		assertEquals(dataSetId, platformConfiguration.getId(), "User has been associated with the wrong dataset!");
		assertEquals(2, dataTransformationErrorRepository.countByPlatformConfigurationId(platformConfiguration.getId()),
		             "Transformation errors have not been persisted!");

		assertDoesNotThrow(() -> databaseService.delete(user));

		assertFalse(existsTable(dataSetId), "Table should be deleted!");
		assertFalse(existsDataConfigration(dataSetId), "Configuration has not been deleted!");
		assertNull(getTestUser().getPlatformConfiguration(), "User association with the dataset has not been removed!");
		assertEquals(0, dataTransformationErrorRepository.countByPlatformConfigurationId(platformConfiguration.getId()),
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

		assertDoesNotThrow(() -> databaseService.storeConfiguration(configName, config, user),
		                   "The configuration could not be stored!");

		final UserEntity updatedUser = getTestUser();

		final PlatformConfigurationEntity platformConfiguration = updatedUser.getPlatformConfiguration();
		assertNotNull(platformConfiguration, "The configuration has not been created!");
		assertTrue(platformConfiguration.getConfigurations().containsKey(configName),
		           "The configuration has not been stored correctly under the user!");
		assertEquals(config, platformConfiguration.getConfigurations().get(configName),
		             "The configuration has not been stored correctly!");
	}

	@Test
	@Transactional
	void storeConfigurationOverwrite() {
		final String configName = "testConfigName";
		final String config = "Test config";

		storeConfiguration(configName, config);

		final UserEntity user = getTestUser();
		final String updatedConfig = "Updated test config";
		assertDoesNotThrow(() -> databaseService.storeConfiguration(configName, updatedConfig, user),
		                   "The configuration could not be updated!");

		final UserEntity updatedUser = getTestUser();

		final PlatformConfigurationEntity platformConfiguration = updatedUser.getPlatformConfiguration();
		assertNotNull(platformConfiguration, "The configuration has not been created!");
		assertTrue(platformConfiguration.getConfigurations().containsKey(configName),
		           "The configuration has not been stored correctly under the user!");
		assertEquals(updatedConfig, platformConfiguration.getConfigurations().get(configName),
		             "The configuration has not been stored correctly!");
	}

	@Test
	void exportDataSet() {
		final TransformationResult transformationResult = TestModelHelper.generateTransformationResult(false);
		final UserEntity user = getTestUser();

		assertDoesNotThrow(() -> databaseService.store(transformationResult, user));

		final DataSet export = assertDoesNotThrow(() -> databaseService.exportDataSet(user, new ArrayList<>()));
		assertEquals(transformationResult.getDataSet(), export, "Data sets do not match!");

		assertDoesNotThrow(() -> databaseService.delete(user));
	}

	@Test
	void exportDataSetColumns() {
		final TransformationResult transformationResult = TestModelHelper.generateTransformationResult(false);
		final UserEntity user = getTestUser();

		assertDoesNotThrow(() -> databaseService.store(transformationResult, user));

		final DataSet export = assertDoesNotThrow(
				() -> databaseService.exportDataSet(user, List.of("column4_integer", "column0_boolean")));

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

		assertDoesNotThrow(() -> databaseService.delete(user));
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
		final String exportedConfig = assertDoesNotThrow(() -> databaseService.exportConfiguration(configName, user),
		                                                 "The configuration could not be exported!");
		assertEquals(config, exportedConfig, "The exported config does not match the original config!");
	}

	@Test
	@Transactional
	void exportConfigurationNoConfiguration() {
		final String configName = "testConfigName";
		final UserEntity user = getTestUser();
		assertThrows(BadDataSetIdException.class, () -> databaseService.exportConfiguration(configName, user),
		             "Configuration should not be present!");
	}

	@Test
	@Transactional
	void exportConfigurationInvalidName() {
		final String configName = "testConfigName";
		final String invalidConfigName = "invalidConfigName";
		final String config = "Test config";
		final UserEntity user = getTestUser();

		storeConfiguration(configName, config);

		assertThrows(BadConfigurationNameException.class,
		             () -> databaseService.exportConfiguration(invalidConfigName, user),
		             "Configuration should not be present!");
	}
}
