package de.kiaim.platform.service;

import de.kiaim.platform.DatabaseTest;
import de.kiaim.platform.TestModelHelper;
import de.kiaim.platform.model.DataSet;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.data.DataRow;
import de.kiaim.platform.model.data.DataType;
import de.kiaim.platform.model.data.configuration.ColumnConfiguration;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.model.entity.DataConfigurationEntity;
import de.kiaim.platform.model.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
		DataConfigurationEntity dataConfiguration = testUser.getDataConfiguration();
		assertNotNull(dataConfiguration, "User has not been associated with the dataset!");
		assertEquals(dataSetId, dataConfiguration.getId(), "User has been associated with the wrong dataset!");
		assertEquals(0, dataTransformationErrorRepository.countByDataConfigurationId(dataConfiguration.getId()), "No transformation errors should have been persisted!");

		assertDoesNotThrow(() -> databaseService.delete(user));

		assertFalse(existsTable(dataSetId), "Table should be deleted!");
		assertFalse(existsDataConfigration(dataSetId), "Configuration has not been deleted!");
		assertNull(getTestUser().getDataConfiguration(), "User association with the dataset has not been removed!");
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
		DataConfigurationEntity dataConfiguration = testUser.getDataConfiguration();
		assertNotNull(dataConfiguration, "User has not been associated with the dataset!");
		assertEquals(dataSetId, dataConfiguration.getId(), "User has been associated with the wrong dataset!");
		assertEquals(2, dataTransformationErrorRepository.countByDataConfigurationId(dataConfiguration.getId()), "Transformation errors have not been persisted!");

		assertDoesNotThrow(() -> databaseService.delete(user));

		assertFalse(existsTable(dataSetId), "Table should be deleted!");
		assertFalse(existsDataConfigration(dataSetId), "Configuration has not been deleted!");
		assertNull(getTestUser().getDataConfiguration(), "User association with the dataset has not been removed!");
		assertEquals(0, dataTransformationErrorRepository.countByDataConfigurationId(dataConfiguration.getId()), "Transformation errors have not been removed!");
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
}
