package de.kiaim.platform.service;

import de.kiaim.platform.helper.DataschemeGenerator;
import de.kiaim.platform.model.entity.DataConfigurationEntity;
import de.kiaim.platform.model.DataSet;
import de.kiaim.platform.model.data.*;
import de.kiaim.platform.model.data.configuration.ColumnConfiguration;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.exception.BadDataSetIdException;
import de.kiaim.platform.exception.InternalDataSetPersistenceException;
import de.kiaim.platform.repository.DataConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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
	 * @return The generated ID of the DataSet.
	 * @throws InternalDataSetPersistenceException If the data set could not be stored due to an internal error.
	 */
	@Transactional
	public long store(final DataSet dataSet) throws InternalDataSetPersistenceException {
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
			throw new InternalDataSetPersistenceException("The Table for the DataSet could not be created!", e);
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
			try {
				delete(dataSetId);
			} catch (BadDataSetIdException ignored) {
			}
			throw new InternalDataSetPersistenceException("The DataSet could not be persisted!", e);
		}

		return dataSetId;
	}

	/**
	 * Exports the configuration of a given data set.
	 *
	 * @param dataSetId ID of the data set.
	 * @return The configuration.
	 * @throws BadDataSetIdException If no data set with the given ID exists.
	 */
	@Transactional
	public DataConfiguration exportDataConfiguration(final long dataSetId) throws BadDataSetIdException {
		existsOrThrow(dataSetId);
		return dataConfigurationRepository.findById(dataSetId).get().getDataConfiguration();
	}

	/**
	 * Exports the DataSet with the given data set ID.
	 *
	 * @param dataSetId ID of the data set to be exported.
	 * @return The DataSet.
	 * @throws BadDataSetIdException If no data set with the given ID exists.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 */
	@Transactional
	public DataSet exportDataSet(final long dataSetId) throws BadDataSetIdException, InternalDataSetPersistenceException {
		final DataConfiguration dataConfiguration = exportDataConfiguration(dataSetId);

		final List<DataRow> dataRows = new ArrayList<>();
		try (final Statement exportStatement = connection.createStatement()) {
			final String exportQuery = "SELECT * FROM " + getTableName(dataSetId) + ";";
			try (final ResultSet resultSet = exportStatement.executeQuery(exportQuery)) {
				while (resultSet.next()) {
					final List<Data> data = new ArrayList<>();
					for (int columnIndex = 0;
					     columnIndex < dataConfiguration.getConfigurations().size(); ++columnIndex) {
						final ColumnConfiguration columnConfiguration = dataConfiguration.getConfigurations()
						                                                                 .get(columnIndex);
						data.add(convertResultToData(resultSet, columnIndex + 1, columnConfiguration.getType()));
					}
					dataRows.add(new DataRow(data));
				}
			}
		} catch (SQLException e) {
			throw new InternalDataSetPersistenceException("The DataSet could not be exported!", e);
		}

		return new DataSet(dataRows, dataConfiguration);
	}

	/**
	 * Removes a DataSet ant the DataConfiguration from the database and deletes the corresponding table.
	 *
	 * @param dataSetId ID of the DataSet.
	 * @throws BadDataSetIdException If no data set with the given ID exists.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 */
	@Transactional
	public void delete(final long dataSetId) throws BadDataSetIdException, InternalDataSetPersistenceException {
		// Check if the dataSetId is valid
		existsOrThrow(dataSetId);

		// Delete the table and its data
		try (final Statement statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS " + getTableName(dataSetId) + ";");
		} catch (SQLException e) {
			throw new InternalDataSetPersistenceException("The DataSet could not be deleted!", e);
		}

		// Delete the configuration
		dataConfigurationRepository.deleteById(dataSetId);
	}

	@Transactional
	public void executeStatement(final String query) throws SQLException {
		try (final Statement statement = connection.createStatement()) {
			statement.execute(query);
		} catch (SQLException e) {
			throw e;
		}
	}

	private String convertDataToString(final Data data) throws InternalDataSetPersistenceException {
		return switch (data.getDataType()) {
			case BOOLEAN -> data.getValue().toString();
			case DATE -> "'" + data.getValue() + "'";
			case DATE_TIME ->
					"'" + data.asDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")) + "'";
			case DECIMAL -> data.getValue().toString();
			case INTEGER -> data.getValue().toString();
			case STRING -> "'" + data.getValue() + "'";
			case UNDEFINED -> throw new InternalDataSetPersistenceException("Undefined data type can not be persisted!");
		};
	}

	private void existsOrThrow(final long dataSetId) throws BadDataSetIdException {
		final boolean exists = dataConfigurationRepository.existsById(dataSetId);
		if (!exists) {
			throw new BadDataSetIdException("No DataSet with the given ID '" + dataSetId + "' found!");
		}
	}

	private Data convertResultToData(final ResultSet resultSet, final int columnIndex,
	                                 final DataType dataType) throws InternalDataSetPersistenceException {
		try {
			switch (dataType) {
				case BOOLEAN -> {
					return new BooleanData(resultSet.getBoolean(columnIndex));
				}
				case DATE_TIME -> {
					return new DateTimeData(resultSet.getTimestamp(columnIndex).toLocalDateTime());
				}
				case DECIMAL -> {
					return new DecimalData(resultSet.getFloat(columnIndex));
				}
				case INTEGER -> {
					return new IntegerData(resultSet.getInt(columnIndex));
				}
				case STRING -> {
					return new StringData(resultSet.getString(columnIndex));
				}
				case DATE -> {
					return new DateData(resultSet.getDate(columnIndex).toLocalDate());
				}
				case UNDEFINED -> {
					throw new InternalDataSetPersistenceException("Undefined data type can not be exported!");
				}
				default -> throw new IllegalStateException("Unexpected value: " + dataType);
			}
		} catch (SQLException e) {
			try {
				throw new InternalDataSetPersistenceException(
						"Failed to convert value " + resultSet.getString(columnIndex) + " to the given DataType '" +
						dataType.name() + "'!");
			} catch (SQLException ex) {
				throw new InternalDataSetPersistenceException(
						"Failed to convert value to the given DataType '" + dataType.name() + "'!");
			}
		}
	}
}
