package de.kiaim.platform.service;

import de.kiaim.model.configuration.data.ColumnConfiguration;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.model.data.*;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.platform.exception.BadColumnNameException;
import de.kiaim.platform.exception.BadConfigurationNameException;
import de.kiaim.platform.helper.DataschemeGenerator;
import de.kiaim.platform.model.DataRowTransformationError;
import de.kiaim.platform.model.DataTransformationError;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.exception.BadDataSetIdException;
import de.kiaim.platform.exception.InternalDataSetPersistenceException;
import de.kiaim.platform.model.entity.DataTransformationErrorEntity;
import de.kiaim.platform.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Service
public class DatabaseService {

	private final Logger LOGGER = LoggerFactory.getLogger(DatabaseService.class);

	final Connection connection;
	final ProjectRepository projectRepository;

	final DataschemeGenerator dataschemeGenerator;

	@Autowired
	public DatabaseService(final DataSource dataSource, final ProjectRepository projectRepository,
	                       final DataschemeGenerator dataschemeGenerator) {
		this.connection = DataSourceUtils.getConnection(dataSource);
		this.projectRepository = projectRepository;
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
	 * Stores the DataConfiguration and associates the configuration with the given project.
	 *
	 * @param dataConfiguration The configuration to be stored.
	 * @param project The project the configuration should be associated with.
	 * @return The ID of the configuration.
	 * @throws BadDataSetIdException If the data has already been stored.
	 * @throws InternalDataSetPersistenceException If the configuration could not be stored.
	 */
	@Transactional
	public long store(final DataConfiguration dataConfiguration, final ProjectEntity project)
			throws BadDataSetIdException, InternalDataSetPersistenceException {
		return store(dataConfiguration, new ArrayList<>(), project);
	}

	/**
	 * Stores the given TransformationResult by storing the DataSet,
	 * the DataConfiguration and the transformation errors into the database and associates them with the given project.
	 * The table for the DataSet will be generated automatically.
	 * Returns an ID to access the data.
	 *
	 * @param transformationResult  TransformationResult to store.
	 * @param project The project the configuration should be associated with.
	 * @return The generated ID of the DataSet.
	 * @throws BadDataSetIdException If the data has already been stored.
	 * @throws InternalDataSetPersistenceException If the data set could not be stored due to an internal error.
	 */
	@Transactional
	public long store(final TransformationResult transformationResult, final ProjectEntity project)
			throws BadDataSetIdException, InternalDataSetPersistenceException  {
		final DataSet dataSet = transformationResult.getDataSet();

		// Store configuration and transformation errors
		final long dataSetId = store(dataSet.getDataConfiguration(), transformationResult.getTransformationErrors(),
		                             project);
		final String tableName = getTableName(dataSetId);

		// Create table
		final String tableQuery = dataschemeGenerator.createSchema(dataSet.getDataConfiguration(), tableName);
		try (final Statement tableStatement = connection.createStatement()) {
			tableStatement.execute(tableQuery);
		} catch (final SQLException e) {
			LOGGER.error("The Table for the DataSet could not be created!", e);
			throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.TABLE_CREATE,
			                                              "The Table for the DataSet could not be created!", e);
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
				delete(project);
			} catch (BadDataSetIdException ignored) {
			}
			LOGGER.error("The DataSet could not be persisted!", e);
			throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.DATA_SET_STORE,
			                                              "The DataSet could not be persisted!", e);
		}

		return dataSetId;
	}

	/**
	 * Stores an arbitrary configuration under the given identifier.
	 * If a configuration with the given name is already present, it will be overwritten.
	 *
	 * @param configurationName Identifier for the configuration.
	 * @param configuration Configuration to store.
	 * @param project The project the configuration should be associated with.
	 * @return The ID of the configuration.
	 */
	@Transactional
	public long storeConfiguration(final String configurationName, final String configuration,
	                               final ProjectEntity project) {
		project.getConfigurations().put(configurationName, configuration);
		return storeDataConfigurationEntity(project);
	}

	/**
	 * Exports the configuration of the data set associated with the given project.
	 *
	 * @param project The project of which the configuration should be exported.
	 * @return The configuration.
	 * @throws BadDataSetIdException If no DataConfiguration is associated with the given project.
	 */
	@Transactional
	public DataConfiguration exportDataConfiguration(final ProjectEntity project) throws BadDataSetIdException {
		return getDataConfigurationOrThrow(project);
	}

	/**
	 * Exports the data set associated with the given project.
	 * Returns the columns with the given names in the given order.
	 * If no column names are provided, all columns are exported.
	 *
	 * @param project The project of which the data set should be exported.
	 * @param columnNames Names of the columns to export. If empty, all columns will be exported.
	 * @return The DataSet.
	 * @throws BadColumnNameException If the data set does not contain a column with the given names.
	 * @throws BadDataSetIdException If no DataConfiguration is associated with the given project.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 */
	@Transactional
	public DataSet exportDataSet(final ProjectEntity project, List<String> columnNames)
			throws InternalDataSetPersistenceException, BadColumnNameException, BadDataSetIdException {
		DataConfiguration dataConfiguration = getDataConfigurationOrThrow(project);

		if (columnNames.isEmpty()) {
			columnNames = dataConfiguration.getColumnNames();
		} else {
			existColumnsOrThrow(dataConfiguration, columnNames);
			dataConfiguration = extractColumns(dataConfiguration, columnNames);
		}

		// Export the data from the database
		final List<DataRow> dataRows = new ArrayList<>();
		try (final Statement exportStatement = connection.createStatement()) {

			final String exportQuery = createSelectQuery(getDataSetId(project), columnNames);

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
			LOGGER.error("The DataSet could not be exported!", e);
			throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.DATA_SET_EXPORT,
			                                              "The DataSet could not be exported!", e);
		}

		return new DataSet(dataRows, dataConfiguration);
	}

	/**
	 * Exports the configuration with the given name
	 * @param configurationName Name of the configuration to export.
	 * @param project The project of which the configuration should be exported.
	 * @return The configuration.
	 * @throws BadConfigurationNameException If the project does not have a configuration with the given name.
	 */
	@Transactional
	public String exportConfiguration(final String configurationName, final ProjectEntity project)
			throws BadConfigurationNameException {
		if (!project.getConfigurations().containsKey(configurationName)) {
			throw new BadConfigurationNameException(BadConfigurationNameException.NOT_FOUND,
			                                        "Project with ID '" + project.getId() +
			                                        "' has no configuration with the name '" + configurationName +
			                                        "'!");
		}

		return project.getConfigurations().get(configurationName);
	}

	/**
	 * Removes the DataSet and the transformation errors associated with the given project from the database
	 * and deletes the corresponding table.
	 *
	 * @param project The project of which the data set should be deleted.
	 * @throws BadDataSetIdException If no data set is associated with the given project.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 */
	@Transactional
	public void delete(final ProjectEntity project)
			throws BadDataSetIdException, InternalDataSetPersistenceException {
		final long dataSetId = getDataSetId(project);

		// Delete the table and its data
		if (existsTable(dataSetId)) {
			try (final Statement statement = connection.createStatement()) {
				statement.execute("DROP TABLE IF EXISTS " + getTableName(dataSetId) + ";");
			} catch (SQLException e) {
				LOGGER.error("The DataSet could not be deleted!", e);
				throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.DATA_SET_DELETE,
				                                              "The DataSet could not be deleted!", e);
			}
		}

		// Delete transformation errors
		project.getDataTransformationErrors().clear();
		projectRepository.save(project);
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
			LOGGER.error("The Configuration could not be stored!", e);
			throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.TABLE_CHECk,
			                                              "The Configuration could not be stored!", e);
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

	private long getDataSetId(final ProjectEntity project) {
		return project.getId();
	}

	/**
	 * Stores the DataConfiguration with the transformation errors
	 * and associates the configuration with the project of the request.
	 *
	 * @param dataConfiguration The configuration to be stored.
	 * @param rowTransformationErrors The transformation errors occurred during reading the corresponding data set.
	 * @param project The project the configuration should be associated with.
	 * @return The ID of the configuration.
	 * @throws BadDataSetIdException If the data has already been stored.
	 * @throws InternalDataSetPersistenceException If the configuration could not be stored.
	 */
	private long store(final DataConfiguration dataConfiguration,
	                   final List<DataRowTransformationError> rowTransformationErrors, final ProjectEntity project)
			throws InternalDataSetPersistenceException, BadDataSetIdException {

		// Check if the data set already has been stored
		if (project.getId() != null && existsTable(project.getId())) {
			throw new BadDataSetIdException(BadDataSetIdException.ALREADY_STORED, "The data has already been stored!");
		}

		project.setDataConfiguration(dataConfiguration);

		for (final DataRowTransformationError rowTransformationError : rowTransformationErrors) {
			for (final DataTransformationError transformationError : rowTransformationError.getDataTransformationErrors()) {

				final DataTransformationErrorEntity transformationErrorEntity = new DataTransformationErrorEntity();

				transformationErrorEntity.setRowIndex(rowTransformationError.getIndex());
				transformationErrorEntity.setColumnIndex(transformationError.getIndex());
				transformationErrorEntity.setErrorType(transformationError.getErrorType());
				transformationErrorEntity.setOriginalValue(
						rowTransformationError.getRawValues().get(transformationError.getIndex()));

				project.addDataRowTransformationError(transformationErrorEntity);
			}
		}

		return storeDataConfigurationEntity(project);
	}

	private long storeDataConfigurationEntity(final ProjectEntity project) {
		projectRepository.save(project);

		// Get ID
		return project.getId();
	}

	private String convertDataToString(final Data data) throws InternalDataSetPersistenceException {
		if (data.getValue() == null) {
			return "null";
		}

		return switch (data.getDataType()) {
			case BOOLEAN -> data.getValue().toString();
			case DATE -> "'" + data.getValue() + "'";
			case DATE_TIME ->
					"'" + data.asDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")) + "'";
			case DECIMAL -> data.getValue().toString();
			case INTEGER -> data.getValue().toString();
			case STRING -> "'" + data.getValue().toString().replace("'", "''") + "'";
			case UNDEFINED -> {
				LOGGER.error("Undefined data type can not be persisted!");
				throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.DATA_TYPE_STORE,
				                                              "Undefined data type can not be persisted!");
			}
		};
	}

	private void existColumnsOrThrow(final DataConfiguration dataConfiguration, final List<String> columnNames)
			throws BadColumnNameException {
		final List<String> dataSetColumns = dataConfiguration.getColumnNames();
		final List<String> unknownColumnNames = columnNames.stream()
		                                                   .filter(Predicate.not(dataSetColumns::contains))
		                                                   .toList();

		if (!unknownColumnNames.isEmpty()) {
			throw new BadColumnNameException(BadColumnNameException.NOT_FOUND,
			                                 "Data set does not contain columns with names: '" +
			                                 String.join("', '", unknownColumnNames) + "'");
		}
	}

	private DataConfiguration getDataConfigurationOrThrow(final ProjectEntity project) throws BadDataSetIdException {
		final DataConfiguration dataConfiguration = project.getDataConfiguration();
		if (dataConfiguration == null) {
			throw new BadDataSetIdException(BadDataSetIdException.NO_CONFIGURATION,
			                                "No configuration for the project with the given ID '" + project.getId() +
			                                "' found!");
		}
		return dataConfiguration;
	}

	private String createSelectQuery(final Long dataSetId, final List<String> columnNames) {
		final List<String> quotedColumnNames = columnNames.stream().map(it -> "\"" + it + "\"").toList();
		return "SELECT " + String.join(",", quotedColumnNames) + " FROM " + getTableName(dataSetId) + ";";
	}

	private Data convertResultToData(final ResultSet resultSet, final int columnIndex,
	                                 final DataType dataType) throws InternalDataSetPersistenceException {

		try {
			switch (dataType) {
				case BOOLEAN -> {
					return new BooleanData((Boolean) resultSet.getObject(columnIndex));
				}
				case DATE_TIME -> {
					final Timestamp timestamp = resultSet.getTimestamp(columnIndex);
					final LocalDateTime localDateTime = timestamp != null ? timestamp.toLocalDateTime() : null;
					return new DateTimeData(localDateTime);
				}
				case DECIMAL -> {
					final BigDecimal bigDecimal = resultSet.getBigDecimal(columnIndex);
					final Float floatValue = bigDecimal != null ? bigDecimal.floatValue() : null;
					return new DecimalData(floatValue);
				}
				case INTEGER -> {
					return new IntegerData((Integer) resultSet.getObject(columnIndex));
				}
				case STRING -> {
					return new StringData(resultSet.getString(columnIndex));
				}
				case DATE -> {
					final Date date = resultSet.getDate(columnIndex);
					final LocalDate localDate = date != null ? date.toLocalDate() : null;
					return new DateData(localDate);
				}
				case UNDEFINED -> {
					LOGGER.error("Undefined data type can not be exported!");
					throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.DATA_TYPE_EXPORT,
					                                              "Undefined data type can not be exported!");
				}
				default -> throw new IllegalStateException("Unexpected value: " + dataType);
			}
		} catch (SQLException e) {
			try {
				final String errorMessage = "Failed to convert value '" + resultSet.getString(columnIndex)
				                            + "' to the given DataType '" + dataType.name() + "'!";
				LOGGER.error(errorMessage, e);
				throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.VALUE_CONVERSION,
				                                              errorMessage, e);
			} catch (SQLException ex) {
				LOGGER.error("Failed to convert value to the given DataType '" + dataType.name() + "'!", ex);
				throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.VALUE_CONVERSION,
				                                              "Failed to convert value to the given DataType '" +
				                                              dataType.name() + "'!", ex);
			}
		}
	}

	private DataConfiguration extractColumns(final DataConfiguration sourceConfiguration,
	                                         final List<String> columnNames) throws BadColumnNameException {
		final DataConfiguration targetConfiguration = new DataConfiguration();

		for (int i = 0; i < columnNames.size(); ++i) {
			final String columnName = columnNames.get(i);

			final ColumnConfiguration columnConfiguration = sourceConfiguration.getColumnConfigurationByColumnName(columnName);

			if (columnConfiguration == null) {
				throw new BadColumnNameException(BadColumnNameException.NOT_FOUND,
				                                 "Data set does not contain a column with name: '" + columnName + "'");
			}

			final var updatedColumnConfiguration = new ColumnConfiguration(i,
			                                                               columnName,
			                                                               columnConfiguration.getType(),
			                                                               columnConfiguration.getScale(),
			                                                               columnConfiguration.getConfigurations());
			targetConfiguration.getConfigurations().add(updatedColumnConfiguration);
		}

		return targetConfiguration;
	}

}
