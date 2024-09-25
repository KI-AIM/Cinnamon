package de.kiaim.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.configuration.data.ColumnConfiguration;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.model.data.*;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.platform.config.SerializationConfig;
import de.kiaim.platform.config.StepConfiguration;
import de.kiaim.platform.exception.*;
import de.kiaim.platform.helper.DataschemeGenerator;
import de.kiaim.platform.model.DataRowTransformationError;
import de.kiaim.platform.model.DataTransformationError;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.dto.DataSetInfo;
import de.kiaim.platform.model.dto.TransformationResultPage;
import de.kiaim.platform.model.dto.LoadDataRequest;
import de.kiaim.platform.model.entity.DataSetEntity;
import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.entity.DataTransformationErrorEntity;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.repository.DataSetRepository;
import de.kiaim.platform.repository.DataTransformationErrorRepository;
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
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class DatabaseService {

	private final Logger LOGGER = LoggerFactory.getLogger(DatabaseService.class);

	private final Connection connection;
	private final DataSetRepository dataSetRepository;
	private final DataTransformationErrorRepository errorRepository;
	private final ProjectRepository projectRepository;

	private final DataschemeGenerator dataschemeGenerator;
	private final ObjectMapper jsonMapper;

	private final DataSetService dataSetService;
	private final StepService stepService;

	@Autowired
	public DatabaseService(final DataSource dataSource, final DataTransformationErrorRepository errorRepository,
	                       final SerializationConfig serializationConfig, final DataSetRepository dataSetRepository,
	                       final ProjectRepository projectRepository, final DataschemeGenerator dataschemeGenerator,
	                       final DataSetService dataSetService, final StepService stepService) {
		this.connection = DataSourceUtils.getConnection(dataSource);
		this.errorRepository = errorRepository;
		jsonMapper = serializationConfig.jsonMapper();
		this.dataSetRepository = dataSetRepository;
		this.projectRepository = projectRepository;
		this.dataschemeGenerator = dataschemeGenerator;
		this.stepService = stepService;
		this.dataSetService = dataSetService;
	}

	/**
	 * Returns the table name for a corresponding DataSet with the given id.
	 *
	 * @param dataSetId ID of the DataSet.
	 * @return Name of the corresponding table.
	 */
	public String getTableName(final long dataSetId) {
		return "dataset_" + String.format("%08d", dataSetId);
	}

	/**
	 * Stores the DataConfiguration and associates the configuration with the data set for the given step in the given configuration.
	 *
	 * @param dataConfiguration The configuration to be stored.
	 * @param project The project of the data set the configuration should be associated with.
	 * @param step The step of the data set.
	 * @throws BadDataSetIdException If the data has already been stored.
	 */
	@Transactional
	public void storeDataConfiguration(final DataConfiguration dataConfiguration, final ProjectEntity project,
	                                   final Step step)
			throws BadDataSetIdException {
		throwIfDataSetIsStored(project, step);
		doStoreDataConfiguration(project, dataConfiguration, step);
	}

	/**
	 * Stores the given TransformationResult by storing the DataSet,
	 * the DataConfiguration and the transformation errors into the database
	 * and associates them with the given step in the given project.
	 * The table for the DataSet will be generated automatically.
	 * Returns an ID to access the data.
	 *
	 * @param transformationResult  TransformationResult to store.
	 * @param project The project of the data set the configuration should be associated with.
	 * @param step The step of the data set.
	 * @return The generated ID of the DataSet.
	 * @throws InternalDataSetPersistenceException If the data set could not be stored due to an internal error.
	 */
	@Transactional
	public long storeTransformationResult(final TransformationResult transformationResult, final ProjectEntity project,
	                                      final Step step) throws InternalDataSetPersistenceException {
		// Delete the existing data set
		if (project.getDataSets().containsKey(step) && project.getDataSets().get(step).isStoredData()) {
			deleteDataSet(project.getDataSets().get(step));
		}

		// Store configuration
		final DataSet dataSet = transformationResult.getDataSet();
		final DataSetEntity dataSetEntity = doStoreDataConfiguration(project, dataSet.getDataConfiguration(), step);

		// Store transformation errors
		for (final DataRowTransformationError rowTransformationError : transformationResult.getTransformationErrors() ) {
			for (final DataTransformationError transformationError : rowTransformationError.getDataTransformationErrors()) {

				final DataTransformationErrorEntity transformationErrorEntity = new DataTransformationErrorEntity();

				transformationErrorEntity.setRowIndex(rowTransformationError.getIndex());
				transformationErrorEntity.setColumnIndex(transformationError.getIndex());
				transformationErrorEntity.setErrorType(transformationError.getErrorType());
				transformationErrorEntity.setOriginalValue(
						rowTransformationError.getRawValues().get(transformationError.getIndex()));

				dataSetEntity.addDataRowTransformationError(transformationErrorEntity);
			}
		}

		projectRepository.save(project);

		storeDataSet(dataSet, dataSetEntity);

		return dataSetEntity.getId();
	}

	/**
	 * Stores the given algorithm configuration
	 * @param step The step of the algorithm.
	 * @param configuration The algorithm configuration to be stored.
	 * @param project The project the configuration should be associated with.
	 * @throws InternalApplicationConfigurationException If the step configuration could not be found.
	 */
	@Transactional
	public void storeConfiguration(final Step step, final String configuration, final ProjectEntity project)
			throws InternalApplicationConfigurationException {
		final StepConfiguration stepConfiguration = stepService.getStepConfiguration(step);
		storeConfiguration(stepConfiguration.getConfigurationName(), configuration, project);
	}

	/**
	 * Stores an arbitrary configuration under the given identifier.
	 * If a configuration with the given name is already present, it will be overwritten.
	 *
	 * @param configurationName Identifier for the configuration.
	 * @param configuration Configuration to store.
	 * @param project The project the configuration should be associated with.
	 */
	@Transactional
	public void storeConfiguration(final String configurationName, final String configuration,
	                               final ProjectEntity project) {
		project.getConfigurations().put(configurationName, configuration);
		projectRepository.save(project);
	}

	/**
	 * Returns the info objects of the data set associated with the given step in the given project.
	 * @param project The project
	 * @param step The step the data sets is associated with.
	 * @return The info object.
	 * @throws BadDataSetIdException If no dataset exists.
	 * @throws InternalDataSetPersistenceException If the internal queries failed.
	 */
	public DataSetInfo getInfo(final ProjectEntity project,
	                           final Step step) throws BadDataSetIdException, InternalDataSetPersistenceException {
		final DataSetEntity dataSetEntity = getDataSetEntityOrThrow(project, step);
		final int rows = countEntries(dataSetEntity.getId());
		final int invalidRows = countInvalidRows(dataSetEntity.getId());
		return new DataSetInfo(rows, invalidRows);
	}

	/**
	 * Exports the configuration of the data set associated with the given project and step.
	 *
	 * @param project The project of which the configuration should be exported.
	 * @return The configuration.
	 * @throws BadDataSetIdException If no DataConfiguration is associated with the given project.
	 * @throws InternalIOException If the DataConfiguration could not be deserialized from the stored JSON.
	 */
	@Transactional
	public DataConfiguration exportDataConfiguration(final ProjectEntity project, final Step step)
			throws BadDataSetIdException, InternalIOException {
		final DataSetEntity dataSetEntity = getDataSetEntityOrThrow(project, step);
		return getDetachedDataConfiguration(dataSetEntity);
	}

	/**
	 * Exports the data set associated with the given project and step.
	 * Returns the columns with the given names in the given order.
	 * If no column names are provided, all columns are exported.
	 *
	 * @param project The project of which the data set should be exported.
	 * @param columnNames Names of the columns to export. If empty, all columns will be exported.
	 * @param step The step of which the data set should be exported.
	 * @return The DataSet.
	 * @throws BadColumnNameException If the data set does not contain a column with the given names.
	 * @throws BadDataSetIdException If no DataConfiguration is associated with the given project.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 * @throws InternalIOException If the DataConfiguration could not be deserialized from the stored JSON.
	 */
	@Transactional
	public DataSet exportDataSet(final ProjectEntity project, List<String> columnNames, final Step step)
			throws InternalDataSetPersistenceException, BadColumnNameException, BadDataSetIdException, InternalIOException {
		final DataSetEntity dataSetEntity = getDataSetEntityOrThrow(project, step);
		return exportDataSet(dataSetEntity, columnNames, false, 0, 0);
	}

	@Transactional
	public TransformationResult exportTransformationResult(final ProjectEntity project, final Step step)
			throws BadDataSetIdException, InternalDataSetPersistenceException, BadColumnNameException, InternalIOException {
		final DataSet dataSet = exportDataSet(project, new ArrayList<>(), step);

		final DataSetEntity dataSetEntity = project.getDataSets().get(step);

		final Map<Integer, DataRowTransformationError> rowErrors = new HashMap<>();
		for (final var error : dataSetEntity.getDataTransformationErrors()) {
			if (!rowErrors.containsKey(error.getRowIndex())) {
				final List<String> o = dataSet.getDataRows()
				                              .get(error.getRowIndex())
				                              .getData()
				                              .stream()
				                              .map(Data::toString)
				                              .collect(Collectors.toList());
				rowErrors.put(error.getRowIndex(), new DataRowTransformationError(error.getRowIndex(), o));
			}
			final var rowError = rowErrors.get(error.getRowIndex());
			rowError.addError(new DataTransformationError(error.getColumnIndex(), error.getErrorType()));
			rowError.getRawValues().set(error.getColumnIndex(), error.getOriginalValue());
		}

		return new TransformationResult(dataSet, rowErrors.values().stream().toList());
	}

	/**
	 * Exports a page of the transformation result associated with the given step in the given project.
	 * Starts at the given page number taking the given page size into account.
	 * Returns the columns with the given names in the given order.
	 * If no column names are provided, all columns are exported.
	 *
	 * @param project The project of which the data set should be exported.
	 * @param columnNames Names of the columns to export. If empty, all columns will be exported.
	 * @param step The step of which the data should be exported.
	 * @param pageNumber The number of the page to be exported.
	 * @param pageSize The number of items per page.
	 * @param loadDataRequest Export settings.
	 * @return The page containing the data and meta-data about the page.
	 * @throws BadColumnNameException If the data set does not contain a column with the given names.
	 * @throws BadDataSetIdException If no DataConfiguration is associated with the given project.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 * @throws InternalIOException If the DataConfiguration could not be deserialized from the stored JSON.
	 */
	@Transactional
	public TransformationResultPage exportTransformationResultPage(final ProjectEntity project, final Step step,
	                                                               final List<String> columnNames, final int pageNumber,
	                                                               final int pageSize,
	                                                               final LoadDataRequest loadDataRequest)
			throws BadDataSetIdException, InternalDataSetPersistenceException, BadColumnNameException, InternalIOException {
		final DataSetEntity dataSetEntity = getDataSetEntityOrThrow(project, step);

		final var startRow = (pageNumber - 1) * pageSize;
		final var endRow = startRow + pageSize;

		final DataSet dataSet = exportDataSet(dataSetEntity, columnNames, true, startRow, pageSize);
		final Set<DataTransformationErrorEntity> errors = errorRepository.findByDataSetIdAndRowIndexBetween(
				dataSetEntity.getId(), startRow, endRow - 1);

		final List<List<Object>> data = dataSetService.encodeDataRows(dataSet, errors, startRow, loadDataRequest);
		final int numberRows = countEntries(dataSetEntity.getId());
		final int numberPages = (int) Math.ceil((float) numberRows / pageSize);

		final Map<Integer, DataRowTransformationError> rowErrors = new HashMap<>();
		for (final var error : errors) {
			if (!rowErrors.containsKey(error.getRowIndex())) {
				final List<String> o = dataSet.getDataRows()
				                              .get(error.getRowIndex() - startRow)
				                              .getData()
				                              .stream()
				                              .map(value -> Objects.toString(value, ""))
				                              .collect(Collectors.toList());
				rowErrors.put(error.getRowIndex(), new DataRowTransformationError(error.getRowIndex() - startRow, o));
			}
			final var rowError = rowErrors.get(error.getRowIndex());
			rowError.addError(new DataTransformationError(error.getColumnIndex(), error.getErrorType()));
			rowError.getRawValues().set(error.getColumnIndex(), error.getOriginalValue());
		}

		final List<DataRowTransformationError> transformationErrors = rowErrors.values().stream().toList();

		return new TransformationResultPage(data, transformationErrors, pageNumber, pageSize, numberRows, numberPages);
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
		// TODO do dynamically
		for (final var step : List.of(Step.VALIDATION, Step.ANONYMIZATION, Step.SYNTHETIZATION)) {
			if (project.getDataSets().containsKey(step)) {
				deleteDataSet(project.getDataSets().get(step));
				project.removeDataSet(step);
			}
		}

		// Delete transformation errors
		projectRepository.save(project);
	}

	/**
	 * Counts the number of rows in the dataset with the given ID.
	 * @param dataSetId The ID of the dataset.
	 * @return The number of rows in the dataset.
	 * @throws InternalDataSetPersistenceException If the Number could not be retrieved.
	 */
	public int countEntries(final long dataSetId) throws InternalDataSetPersistenceException {
		final String countQuery = "SELECT count(*) FROM " + getTableName(dataSetId) + ";";
		try (final Statement countStatement = connection.createStatement()) {
			try (ResultSet resultSet = countStatement.executeQuery(countQuery)) {
				resultSet.next();
				return resultSet.getInt(1);
			}
		} catch (SQLException e) {
			throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.DATA_SET_COUNT,
			                                              "Failed to count rows for dataset with ID '" + dataSetId +
			                                              "'!", e);
		}
	}

	/**
	 * Counts the number of rows with at least one transformation error in the data set with the given ID.
	 * @param dataSetId The ID of the dataset.
	 * @return The number of invalid rows.
	 */
	public int countInvalidRows(final long dataSetId) {
		return (int) errorRepository.countDistinctRowIndexByDataSetId(dataSetId);
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
			statement.setQueryTimeout(20);
			statement.execute(query);
		}
	}

	private DataSetEntity doStoreDataConfiguration(final ProjectEntity project,
	                                               final DataConfiguration dataConfiguration,
	                                               final Step step) {
		final DataSetEntity dataSetEntity;

		if (!project.getDataSets().containsKey(step)) {
			dataSetEntity = new DataSetEntity();
			project.putDataSet(step, dataSetEntity);
		} else {
			dataSetEntity = project.getDataSets().get(step);
		}

		dataSetEntity.setDataConfiguration(dataConfiguration);

		projectRepository.save(project);

		return project.getDataSets().get(step);
	}

	private void storeDataSet(final DataSet dataSet, final DataSetEntity dataSetEntity) throws InternalDataSetPersistenceException {
		final String tableName = getTableName(dataSetEntity.getId());

		// Create table
		final String tableQuery = dataschemeGenerator.createSchema(dataSet.getDataConfiguration(), tableName);
		try {
			executeStatement(tableQuery);
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
				deleteDataSet(dataSetEntity);
			} catch (InternalDataSetPersistenceException ignored) {
			}
			LOGGER.error("The DataSet could not be persisted!", e);
			throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.DATA_SET_STORE,
			                                              "The DataSet could not be persisted!", e);
		}

		dataSetEntity.setStoredData(true);
		projectRepository.save(dataSetEntity.getProject());
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

	private DataSet exportDataSet(final DataSetEntity dataSetEntity, List<String> columnNames, final boolean pagination,
	                              final int startRow, final int pageSize)
			throws BadColumnNameException, InternalDataSetPersistenceException, InternalIOException {
		DataConfiguration dataConfiguration = getDetachedDataConfiguration(dataSetEntity);

		if (columnNames.isEmpty()) {
			columnNames = dataConfiguration.getColumnNames();
		} else {
			existColumnsOrThrow(dataConfiguration, columnNames);
			dataConfiguration = extractColumns(dataConfiguration, columnNames);
		}

		// Export the data from the database
		final List<DataRow> dataRows = new ArrayList<>();
		try (final Statement exportStatement = connection.createStatement()) {

			final String exportQuery = createSelectQuery(dataSetEntity.getId(), columnNames, pagination, startRow, pageSize);

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

	private DataSetEntity getDataSetEntityOrThrow(final ProjectEntity project, final Step step) throws BadDataSetIdException {
		if (!project.getDataSets().containsKey(step)) {
			throw new BadDataSetIdException(BadDataSetIdException.NO_DATA_SET, "The project '" + project.getId() +
			                                                                   "' does not contain a data set for step '" +
			                                                                   step.name() + "'!");
		}
		return project.getDataSets().get(step);
	}

	private DataConfiguration getDetachedDataConfiguration(
			final DataSetEntity dataSetEntity) throws InternalIOException {
		final String json = dataSetRepository.getDataConfiguration(dataSetEntity.getId());

		try {
			return jsonMapper.readValue(json, DataConfiguration.class);
		} catch (JsonProcessingException e) {
			throw new InternalIOException(InternalIOException.DATA_CONFIGURATION_DESERIALIZATION,
			                              "Failed to export data configuration because of a failed deserialization!",
			                              e);
		}
	}

	private String createSelectQuery(final Long dataSetId, final List<String> columnNames, final boolean pagination,
	                                 final int startRow, final int pageSize) {
		final List<String> quotedColumnNames = columnNames.stream().map(it -> "\"" + it + "\"").toList();
		String query = "SELECT " + String.join(",", quotedColumnNames) + " FROM " + getTableName(dataSetId);
		if (pagination) {
			query += " LIMIT " + pageSize + " OFFSET " + startRow;
		}
		query += ";";
		return query;
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

	private void deleteDataSet(final DataSetEntity dataSet) throws InternalDataSetPersistenceException {
		// Delete the table and its data
		if (existsTable(dataSet.getId())) {
			try {
				executeStatement("DROP TABLE IF EXISTS " + getTableName(dataSet.getId()) + ";");
			} catch (SQLException e) {
				LOGGER.error("The DataSet could not be deleted!", e);
				throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.DATA_SET_DELETE,
				                                              "The DataSet could not be deleted!", e);
			}
		}

		dataSet.getDataTransformationErrors().clear();
		dataSet.setStoredData(false);
		dataSetRepository.save(dataSet);
	}

	/**
	 * Check if the data set already has been stored.
	 */
	private void throwIfDataSetIsStored(final ProjectEntity project, final Step step) throws BadDataSetIdException {
		if (project.getDataSets().containsKey(step) && project.getDataSets().get(step).isStoredData()) {
			throw new BadDataSetIdException(BadDataSetIdException.ALREADY_STORED, "The data has already been stored!");
		}
	}

}
