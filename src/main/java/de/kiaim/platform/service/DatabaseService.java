package de.kiaim.platform.service;

import de.kiaim.platform.helper.DataschemeGenerator;
import de.kiaim.platform.model.entity.DataConfigurationEntity;
import de.kiaim.platform.model.DataSet;
import de.kiaim.platform.model.data.*;
import de.kiaim.platform.model.data.configuration.ColumnConfiguration;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.exception.BadDataSetIdException;
import de.kiaim.platform.exception.InternalDataSetPersistenceException;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.repository.DataConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DatabaseService {

	final Connection connection;
	final DataConfigurationRepository dataConfigurationRepository;

	final DataschemeGenerator dataschemeGenerator;
	final UserService userService;

	@Autowired
	public DatabaseService(DataSource dataSource, DataConfigurationRepository dataConfigurationRepository,
	                       DataschemeGenerator dataschemeGenerator, UserService userService) {
		this.connection = DataSourceUtils.getConnection(dataSource);
		this.dataConfigurationRepository = dataConfigurationRepository;
		this.dataschemeGenerator = dataschemeGenerator;
		this.userService = userService;
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
	 * Stores the DataConfiguration and associates the configuration with the user of the request.
	 *
	 * @param dataConfiguration The configuration to be store.
	 * @param user The user the configuration should be associated with.
	 * @return The ID of the configuration.
	 * @throws BadDataSetIdException If the data has already been stored.
	 * @throws InternalDataSetPersistenceException If the configuration could not be stored.
	 */
	@Transactional
	public long store(final DataConfiguration dataConfiguration, final UserEntity user)
			throws BadDataSetIdException, InternalDataSetPersistenceException {

		// Store configuration
		final DataConfigurationEntity dataConfigurationEntity;
		if (user.getDataConfiguration() != null) {
			dataConfigurationEntity = user.getDataConfiguration();
			if (existsTable(dataConfigurationEntity.getId())) {
				throw new BadDataSetIdException("The data has already been stored!");
			}
		} else {
			dataConfigurationEntity = new DataConfigurationEntity();
		}

		dataConfigurationEntity.setDataConfiguration(dataConfiguration);
		dataConfigurationRepository.save(dataConfigurationEntity);

		// Get id
		final long dataSetId = dataConfigurationEntity.getId();

		// Set current data configuration for the user
		userService.setConfigurationToUser(dataConfigurationEntity, user);

		return dataSetId;
	}

	/**
	 * Stores the given DataSet and the DataConfiguration into the database and associates them with the given user.
	 * The table for the DataSet will be generated automatically.
	 * Returns an ID to access the data.
	 *
	 * @param dataSet DataSet to store.
	 * @param user The user the configuration should be associated with.
	 * @return The generated ID of the DataSet.
	 * @throws BadDataSetIdException If the data has already been stored.
	 * @throws InternalDataSetPersistenceException If the data set could not be stored due to an internal error.
	 */
	@Transactional
	public long store(final DataSet dataSet, final UserEntity user)
			throws BadDataSetIdException, InternalDataSetPersistenceException  {

		// Store configuration
		final long dataSetId = store(dataSet.getDataConfiguration(), user);
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
				delete(user);
			} catch (BadDataSetIdException ignored) {
			}
			throw new InternalDataSetPersistenceException("The DataSet could not be persisted!", e);
		}

		return dataSetId;
	}

	/**
	 * Exports the configuration of the data set associated with the given user.
	 *
	 * @param user The user of which the configuration should be exported.
	 * @return The configuration.
	 * @throws BadDataSetIdException If no data set is associated with the given user.
	 */
	@Transactional
	public DataConfiguration exportDataConfiguration(final UserEntity user) throws BadDataSetIdException {
		final long dataSetId = getDataSetIdOrThrow(user);
		return getOrThrow(dataSetId).getDataConfiguration();
	}

	/**
	 * Exports the data set associated with the given user.
	 *
	 * @param user The user of which the data set should be exported.
	 * @return The DataSet.
	 * @throws BadDataSetIdException If no data set is associated with the given user.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 */
	@Transactional
	public DataSet exportDataSet(final UserEntity user) throws BadDataSetIdException, InternalDataSetPersistenceException {
		final long dataSetId = getDataSetIdOrThrow(user);
		final DataConfiguration dataConfiguration = exportDataConfiguration(user);

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
	 * Removes the DataSet and the DataConfiguration associated with the given user from the database
	 * and deletes the corresponding table.
	 *
	 * @param user The user of which the data set should be deleted.
	 * @throws BadDataSetIdException If no data set is associated with the given user.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 */
	@Transactional
	public void delete(final UserEntity user)
			throws BadDataSetIdException, InternalDataSetPersistenceException {
		final long dataSetId = getDataSetIdOrThrow(user);

		// Check if the dataSetId is valid
		existsOrThrow(dataSetId);

		// Remove configuration from the user
		userService.removeConfigurationFromUser(user);

		// Delete the table and its data
		if (existsTable(dataSetId)) {
			try (final Statement statement = connection.createStatement()) {
				statement.execute("DROP TABLE IF EXISTS " + getTableName(dataSetId) + ";");
			} catch (SQLException e) {
				throw new InternalDataSetPersistenceException("The DataSet could not be deleted!", e);
			}
		}

		// Delete the configuration
		dataConfigurationRepository.deleteById(dataSetId);
	}

	/**
	 * Checks if a table for the data set with the given ID exists.
	 * @param dataSetId ID to be checked.
	 * @return True if the table exists, false if not.
	 * @throws InternalDataSetPersistenceException If the SQL statement could not be executed.
	 */
	public boolean existsTable(final long dataSetId) throws InternalDataSetPersistenceException {
		final String existsQuery = "SELECT 1 FROM pg_class WHERE relname = ? AND relkind = 'r'";
		try (final PreparedStatement existTableQuery = connection.prepareStatement(existsQuery)) {
			existTableQuery.setString(1, getTableName(dataSetId));
			try (final ResultSet resultSet = existTableQuery.executeQuery()) {
				return resultSet.next();
			}
		} catch (SQLException e) {
			throw new InternalDataSetPersistenceException("The Configuration could not be stored!", e);
		}
	}

	/**
	 * Executes a query in the database.
	 * @param query Query to be executed.
	 * @throws SQLException If the query could not be executed successfully.
	 */
	@Transactional
	public void executeStatement(final String query) throws SQLException {
		try (final Statement statement = connection.createStatement()) {
			statement.execute(query);
		}
	}

	private long getDataSetIdOrThrow(final UserEntity user) throws BadDataSetIdException {
		if (user.getDataConfiguration() == null) {
			throw new BadDataSetIdException("User has no configuration!");
		}
		return user.getDataConfiguration().getId();
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

	private DataConfigurationEntity getOrThrow(final long dataSetId) throws BadDataSetIdException {
		final Optional<DataConfigurationEntity> config = dataConfigurationRepository.findById(dataSetId);
		if (config.isEmpty()) {
			throw new BadDataSetIdException("No DataSet with the given ID '" + dataSetId + "' found!");
		}
		return config.get();
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
