package de.kiaim.platform.service;

import de.kiaim.platform.DatabaseTest;
import de.kiaim.platform.TestModelHelper;
import de.kiaim.platform.model.DataSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;


class DatabaseServiceTest extends DatabaseTest {

	@Autowired
	DatabaseService databaseService;

	@Test
	void storeAndDelete() {
		final DataSet dataSet = TestModelHelper.generateDataSet();

		long id = assertDoesNotThrow(() -> databaseService.store(dataSet));

		assertTrue(existsTable(id), "Table could not be found!");
		assertEquals(2, countEntries(id), "Number of entries wrong!");
		assertTrue(existsDataConfigration(id), "Configuration has not been persisted!");

		assertDoesNotThrow(() -> databaseService.delete(id));

		assertFalse(existsTable(id), "Table should be deleted!");
		assertFalse(existsDataConfigration(id), "Configuration has not been deleted!");
	}
}
