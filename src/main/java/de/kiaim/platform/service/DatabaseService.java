package de.kiaim.platform.service;

import de.kiaim.platform.helper.DataschemeGenerator;
import de.kiaim.platform.model.DataConfigurationEntity;
import de.kiaim.platform.model.DataSet;
import de.kiaim.platform.model.data.Data;
import de.kiaim.platform.model.data.DataRow;
import de.kiaim.platform.model.data.exception.DataSetPersistanceException;
import de.kiaim.platform.repository.DataConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class DatabaseService {

	final Connection connection;
	final DataConfigurationRepository dataConfigurationRepository;

	final DataschemeGenerator dataschemeGenerator;

	@Autowired
	public DatabaseService(DataSource dataSource, DataConfigurationRepository dataConfigurationRepository,
	                       DataschemeGenerator dataschemeGenerator) {
		this.connection = DataSourceUtils.getConnection(dataSource);
		this.dataConfigurationRepository = dataConfigurationRepository;
		this.dataschemeGenerator = dataschemeGenerator;
	}

	/**
	 * Returns the table name for a corresponding DataSet with the given id.
	 *
	 * @param dataSetId ID of the DataSet.
	 * @return Name of the corresponding table.
	 */
	public String getTableName(final long dataSetId) {
		return "data_set_" + String.format("%08d", dataSetId);
	}

	/**
	 * Stores the given DataSet and the DataConfiguration into the database.
	 * The table for the DataSet will be generated automatically.
	 * Returns an ID to access the data.
	 *
	 * @param dataSet DataSet to store.
	 * @return The generated ID of the DataSet
	 */
	public long store(final DataSet dataSet) throws DataSetPersistanceException {
		// Store configuration
		final DataConfigurationEntity dataConfigurationEntity = new DataConfigurationEntity();
		dataConfigurationEntity.setDataConfiguration(dataSet.getDataConfiguration());
		dataConfigurationRepository.save(dataConfigurationEntity);

		// Get id and name
		final long dataSetId = dataConfigurationEntity.getId();
		final String tableName = getTableName(dataSetId);

		// Create table
		final String tableQuery = dataschemeGenerator.createSchema(dataSet.getDataConfiguration(), tableName);
		try (final Statement tableStatement = connection.createStatement()) {
			tableStatement.execute(tableQuery);
		} catch (SQLException e) {
			throw new DataSetPersistanceException("The Table for the DataSet could not be created!", e);
		}

		// Insert data
		try (final Statement insertStatement = connection.createStatement()) {
			for (final DataRow dataRow : dataSet.getDataRows()) {
				final List<String> stringRow = new ArrayList<>();
				for (final Data data : dataRow.getData()) {
					stringRow.add(convertDataToString(data));
				}
				final String values = String.join(",", stringRow);
				insertStatement.execute("INSERT INTO " + tableName + " VALUES (" + values + ")");
			}
		} catch (SQLException e) {
			delete(dataSetId);
			throw new DataSetPersistanceException("The DataSet could not be persisted!", e);
		}

		return dataSetId;
	}

	/**
	 * Removes a DataSet ant the DataConfiguration from the database and deletes the corresponding table.
	 *
	 * @param dataSetId ID of the DataSet.
	 */
	public void delete(final long dataSetId) throws DataSetPersistanceException {
		// Delete the table and its data
		try (final Statement statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS " + getTableName(dataSetId) + ";");
		} catch (SQLException e) {
			throw new DataSetPersistanceException("The DataSet could not be deleted!", e);
		}

		// Delete the configuration
		dataConfigurationRepository.deleteById(dataSetId);
	}

	private String convertDataToString(final Data data) throws DataSetPersistanceException {
		return switch (data.getDataType()) {
			case BOOLEAN -> data.getValue().toString();
			case DATE -> "'" + data.getValue() + "'";
			case DATE_TIME -> "'" + data.asDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")) + "'";
			case DECIMAL -> data.getValue().toString();
			case INTEGER -> data.getValue().toString();
			case STRING -> "'" + data.getValue() + "'";
			case UNDEFINED -> throw new DataSetPersistanceException("Undefined data type can not be persisted");
		};
	}
}
