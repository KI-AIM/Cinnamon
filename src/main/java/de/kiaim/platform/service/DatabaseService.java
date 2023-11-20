package de.kiaim.platform.service;

import de.kiaim.platform.helper.DataschemeGenerator;
import de.kiaim.platform.model.DataSet;
import de.kiaim.platform.model.data.Data;
import de.kiaim.platform.model.data.DataRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class DatabaseService {

	final Connection connection;

	final DataschemeGenerator dataschemeGenerator;

	@Autowired
	public DatabaseService(DataSource dataSource, DataschemeGenerator dataschemeGenerator) {
		this.connection = DataSourceUtils.getConnection(dataSource);
		this.dataschemeGenerator = dataschemeGenerator;
	}

	public String getTableName(final long dataSetId) {
		return "data_set_" + String.format("%08d", dataSetId);
	}

	public long store(final DataSet dataSet) {
		long dataSetId = new Random().nextLong(99999999);
		final String tableName = getTableName(dataSetId);

		// Create table
		final String tableQuery = dataschemeGenerator.createSchema(dataSet.getDataConfiguration(), tableName);
		try (final Statement tableStatement = connection.createStatement()) {
			tableStatement.execute(tableQuery);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		// Insert data
		try (final Statement insertStatement = connection.createStatement()) {
			for (final DataRow dataRow : dataSet.getDataRows()) {
				String values = dataRow.getData()
				                       .stream()
				                       .map(this::convertDataToString)
				                       .collect(Collectors.joining(","));
				insertStatement.execute("INSERT INTO " + tableName + " VALUES (" + values + ")");
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		return dataSetId;
	}

	public void delete(final long dataSetId) {
		try (final Statement statement = connection.createStatement()) {
			statement.execute("DROP TABLE " + getTableName(dataSetId));
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private String convertDataToString(final Data data) {
		return switch (data.getDataType()) {
			case BOOLEAN -> data.getValue().toString();
			case DATE -> "'" + data.getValue() + "'";
			case DATE_TIME -> "'" + data.asDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")) + "'";
			case DECIMAL -> data.getValue().toString();
			case INTEGER -> data.getValue().toString();
			case STRING -> "'" + data.getValue() + "'";
		};
	}
}
