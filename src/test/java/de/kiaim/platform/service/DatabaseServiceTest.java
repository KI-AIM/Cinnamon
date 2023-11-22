package de.kiaim.platform.service;

import de.kiaim.platform.PlatformApplication;
import de.kiaim.platform.TestModelHelper;
import de.kiaim.platform.model.DataSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;


@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = PlatformApplication.class)
@ActiveProfiles("test")
//@Transactional // Will block the DROP TABLE statement
class DatabaseServiceTest {

	@Autowired
	DataSource dataSource;
	Connection connection;

	@Autowired
	DatabaseService databaseService;

	@Test
	void store() {
		final DataSet dataSet = TestModelHelper.generateDataSet();

		long id = assertDoesNotThrow(() -> databaseService.store(dataSet));

		assertTrue(existsTable(id), "Table could not be found!");
		assertEquals(2, countEntries(id), "Number of entries wrong!");

		assertDoesNotThrow(() -> databaseService.delete(id));

		assertFalse(existsTable(id), "Table should be deleted!");
	}

	@BeforeEach
	void setUp() {
		if (connection == null) {
			connection = DataSourceUtils.getConnection(dataSource);
		}
	}

	private boolean existsTable(final long id) {
		final String existsQuery = "SELECT 1 FROM pg_class WHERE relname = ? AND relkind = 'r'";
		try (final PreparedStatement existTableQuery = connection.prepareStatement(existsQuery)) {
			existTableQuery.setString(1, databaseService.getTableName(id));
			try (final ResultSet resultSet = existTableQuery.executeQuery()) {
				return resultSet.next();
			}
		} catch (SQLException e) {
			fail(e);
			return false;
		}
	}

	private int countEntries(final long id) {
		final String countQuery = "SELECT count(*) FROM " + databaseService.getTableName(id);
		try (final Statement countStatement = connection.createStatement()) {
			try (ResultSet resultSet = countStatement.executeQuery(countQuery)) {
				resultSet.next();
				return resultSet.getInt(1);
			}
		} catch (SQLException e) {
			fail(e);
			return 0;
		}
	}

}