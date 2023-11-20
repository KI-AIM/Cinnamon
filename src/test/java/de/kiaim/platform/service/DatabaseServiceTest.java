package de.kiaim.platform.service;

import de.kiaim.platform.model.DataConfiguration;
import de.kiaim.platform.model.DataSet;
import de.kiaim.platform.model.data.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class DatabaseServiceTest {

	@Autowired
	DataSource dataSource;

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
		final DataConfiguration dataConfiguration = new DataConfiguration(dataTypes);

		final List<Data> data1 = List.of(new BooleanData(true),
		                                 new DateData(LocalDate.of(2023, 11, 20)),
		                                 new DateTimeData(LocalDateTime.of(2023, 11, 20, 12, 50, 27, 123)),
		                                 new DecimalData(4.2f),
		                                 new IntegerData(42),
		                                 new StringData("Hello World!"));
		final List<Data> data2 = List.of(new BooleanData(false),
		                                 new DateData(LocalDate.of(2023, 11, 20)),
		                                 new DateTimeData(LocalDateTime.of(2023, 11, 20, 12, 50, 27, 123)),
		                                 new DecimalData(2.4f),
		                                 new IntegerData(24),
		                                 new StringData("Bye World!"));
		final DataRow dataRow1 = new DataRow(data1);
		final DataRow dataRow2 = new DataRow(data2);
		final List<DataRow> dataRows = List.of(dataRow1, dataRow2);

		final DataSet dataSet = new DataSet(dataRows, dataConfiguration);

		long id = assertDoesNotThrow(() -> {return databaseService.store(dataSet);});

		try (final PreparedStatement existTableQuery = dataSource.getConnection().prepareStatement(
				"SELECT 1 FROM pg_class WHERE relname = ? AND relkind = 'r'")) {
			existTableQuery.setLong(1, id);
			try (final ResultSet resultSet = existTableQuery.executeQuery()) {
				while (resultSet.next()) {
					assertEquals(1, resultSet.getInt(1));
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

	}

	@Test
	void delete() {
	}
}