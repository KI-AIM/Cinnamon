package de.kiaim.platform.service;

import de.kiaim.platform.PlatformApplication;
import de.kiaim.platform.model.DataConfiguration;
import de.kiaim.platform.model.DataSet;
import de.kiaim.platform.model.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = PlatformApplication.class)
@ActiveProfiles("test")
@Transactional
class DatabaseServiceTest {

	@Autowired
	DataSource dataSource;
	Connection connection;

	@Autowired
	DatabaseService databaseService;

	@Test
	void store() {
		final List<DataType> dataTypes = List.of(DataType.BOOLEAN,
		                                         DataType.DATE,
		                                         DataType.DATE_TIME,
		                                         DataType.DECIMAL,
		                                         DataType.INTEGER,
		                                         DataType.STRING);
		final DataConfiguration dataConfiguration = new DataConfiguration();
		dataConfiguration.setDataTypes(dataTypes);

		final List<Data> data1 = List.of(new BooleanData(true),
		                                 new DateData(LocalDate.of(2023, 11, 20)),
		                                 new DateTimeData(LocalDateTime.of(2023, 11, 20, 12, 50, 27, 123456789)),
		                                 new DecimalData(4.2f),
		                                 new IntegerData(42),
		                                 new StringData("Hello World!"));
		final List<Data> data2 = List.of(new BooleanData(false),
		                                 new DateData(LocalDate.of(2023, 11, 20)),
		                                 new DateTimeData(LocalDateTime.of(2023, 11, 20, 12, 50, 27, 123456789)),
		                                 new DecimalData(2.4f),
		                                 new IntegerData(24),
		                                 new StringData("Bye World!"));
		final DataRow dataRow1 = new DataRow(data1);
		final DataRow dataRow2 = new DataRow(data2);
		final List<DataRow> dataRows = List.of(dataRow1, dataRow2);

		final DataSet dataSet = new DataSet(dataRows, dataConfiguration);

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