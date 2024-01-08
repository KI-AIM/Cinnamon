package de.kiaim.platform.service;

import de.kiaim.platform.DatabaseTest;
import de.kiaim.platform.TestModelHelper;
import de.kiaim.platform.model.DataSet;
import de.kiaim.platform.model.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;


class DatabaseServiceTest extends DatabaseTest {

	@Autowired
	DatabaseService databaseService;

	@Test
	void storeAndDelete() {
		final DataSet dataSet = TestModelHelper.generateDataSet();
		final UserEntity user = getTestUser();

		long dataSetId = assertDoesNotThrow(() -> databaseService.store(dataSet, user));

		UserEntity testUser = getTestUser();
		assertTrue(existsTable(dataSetId), "Table could not be found!");
		assertEquals(2, countEntries(dataSetId), "Number of entries wrong!");
		assertTrue(existsDataConfigration(dataSetId), "Configuration has not been persisted!");
		assertNotNull(testUser.getDataConfiguration(), "User has not been associated with the dataset!");
		assertEquals(dataSetId, testUser.getDataConfiguration().getId(), "User has been associated with the wrong dataset!");

		assertDoesNotThrow(() -> databaseService.delete(user));

		assertFalse(existsTable(dataSetId), "Table should be deleted!");
		assertFalse(existsDataConfigration(dataSetId), "Configuration has not been deleted!");
		assertNull(getTestUser().getDataConfiguration(), "User association with the dataset has not been removed!");
	}
}
