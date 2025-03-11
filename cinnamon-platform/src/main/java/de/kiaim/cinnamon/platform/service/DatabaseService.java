package de.kiaim.cinnamon.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.configuration.data.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.data.*;
import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.configuration.Job;
import de.kiaim.cinnamon.platform.config.SerializationConfig;
import de.kiaim.cinnamon.platform.model.dto.*;
import de.kiaim.cinnamon.platform.model.entity.*;
import de.kiaim.cinnamon.platform.repository.DataProcessingRepository;
import de.kiaim.cinnamon.platform.repository.DataSetRepository;
import de.kiaim.cinnamon.platform.repository.DataTransformationErrorRepository;
import de.kiaim.cinnamon.platform.repository.ProjectRepository;
import de.kiaim.cinnamon.platform.helper.DataschemeGenerator;
import de.kiaim.cinnamon.platform.model.DataRowTransformationError;
import de.kiaim.cinnamon.platform.model.DataTransformationError;
import de.kiaim.cinnamon.platform.model.TransformationResult;
import de.kiaim.cinnamon.platform.model.enumeration.HoldOutSelector;
import de.kiaim.cinnamon.platform.model.enumeration.ProcessStatus;
import de.kiaim.cinnamon.platform.model.enumeration.RowSelector;
import de.kiaim.cinnamon.platform.model.file.FileConfiguration;
import de.kiaim.cinnamon.platform.processor.DataProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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
	private final DataProcessingRepository dataProcessingRepository;
	private final DataSetRepository dataSetRepository;
	private final DataTransformationErrorRepository errorRepository;
	private final ProjectRepository projectRepository;

	private final DataschemeGenerator dataschemeGenerator;
	private final ObjectMapper jsonMapper;

	private final DataSetService dataSetService;
	private final DataProcessorService dataProcessorService;
	private final StepService stepService;

	@Autowired
	public DatabaseService(final DataSource dataSource, final DataProcessingRepository dataProcessingRepository,
	                       final DataTransformationErrorRepository errorRepository,
	                       final SerializationConfig serializationConfig, final DataSetRepository dataSetRepository,
	                       final ProjectRepository projectRepository, final DataschemeGenerator dataschemeGenerator,
	                       final DataSetService dataSetService, final DataProcessorService dataProcessorService,
	                       final StepService stepService) {
		this.connection = DataSourceUtils.getConnection(dataSource);
		this.dataProcessingRepository = dataProcessingRepository;
		this.errorRepository = errorRepository;
		jsonMapper = serializationConfig.jsonMapper();
		this.dataSetRepository = dataSetRepository;
		this.projectRepository = projectRepository;
		this.dataschemeGenerator = dataschemeGenerator;
		this.dataSetService = dataSetService;
		this.dataProcessorService = dataProcessorService;
		this.stepService = stepService;
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
	 * Stores the given file.
	 * If a data set is present and has not been confirmed, the data set will be deleted.
	 *
	 * @param project           The project where the file should be stored.
	 * @param file              The file to be stored.
	 * @param fileConfiguration The file configuration to be stored.
	 * @return General information about the file.
	 * @throws BadDataSetIdException               If the data set has already been confirmed.
	 * @throws BadFileException                    If the file could not be read.
	 * @throws InternalDataSetPersistenceException If the data set could not be deleted.
	 * @throws InternalMissingHandlingException    If no processor for the file type of the file could be found.
	 */
	@Transactional
	public FileInformation storeFile(final ProjectEntity project, final MultipartFile file,
	                                 final FileConfiguration fileConfiguration) throws BadDataSetIdException, BadFileException, InternalDataSetPersistenceException, InternalMissingHandlingException {
		deleteDataSetIfNotConfirmedOrThrow(project.getOriginalData().getDataSet());

		dataProcessorService.validateFileOrThrow(file);

		final FileConfigurationEntity fileConfigurationEntity = switch (fileConfiguration.getFileType()) {
			case CSV -> new CsvFileConfigurationEntity(fileConfiguration.getCsvFileConfiguration());
			case FHIR -> new FhirFileConfigurationEntity();
			case XLSX -> new XlsxFileConfigurationEntity(fileConfiguration.getXlsxFileConfiguration());
		};

		final FileEntity fileEntity = new FileEntity();
		fileEntity.setName(file.getOriginalFilename());
		fileEntity.setFileConfiguration(fileConfigurationEntity);

		try {
			fileEntity.setFile(file.getBytes());
		} catch (final IOException e) {
			throw new BadFileException(BadFileException.NOT_READABLE, "Could not read file");
		}

		final DataProcessor dataProcessor = dataProcessorService.getDataProcessor(fileConfiguration.getFileType());
		final int numberOfAttributes = dataProcessor.getNumberColumns(new ByteArrayInputStream(fileEntity.getFile()),
		                                                              fileConfigurationEntity);
		fileEntity.setNumberOfAttributes(numberOfAttributes);

		project.getOriginalData().setFile(fileEntity);
		projectRepository.save(project);

		return getFileInformation(project);
	}

	@Transactional
	public FileInformation getFileInformation(final ProjectEntity project) {
		final var fileInformation = new FileInformation();

		final FileEntity file = project.getOriginalData().getFile();
		if (file != null) {
			fileInformation.setName(file.getName());
			fileInformation.setNumberOfAttributes(file.getNumberOfAttributes());
			fileInformation.setType(file.getFileConfiguration().getFileType());
		}

		return fileInformation;
	}

	/**
	 * Stores the DataConfiguration and associates the configuration with the data set for the given step in the given configuration.
	 *
	 * @param dataConfiguration The configuration to be stored.
	 * @param project           The project of the data set the configuration should be associated with.
	 * @throws BadDataConfigurationException       If the data configuration is not valid.
	 * @throws BadDataSetIdException               If the data has already been confirmed.
	 * @throws BadStateException                   If the file for the dataset has not been selected.
	 * @throws InternalDataSetPersistenceException If the data set could not be deleted.
	 */
	@Transactional
	public void storeOriginalDataConfiguration(final DataConfiguration dataConfiguration, final ProjectEntity project)
			throws BadDataConfigurationException, BadDataSetIdException, BadStateException, InternalDataSetPersistenceException {
		deleteDataSetIfNotConfirmedOrThrow(project.getOriginalData().getDataSet());
		doStoreOriginalDataConfiguration(project, dataConfiguration);
	}

	/**
	 * Stores the given TransformationResult as the original data by storing the DataSet,
	 * the DataConfiguration and the transformation errors into the database
	 * and associates them with the given step in the given project.
	 * The table for the DataSet will be generated automatically.
	 * Returns an ID to access the data.
	 *
	 * @param transformationResult The transformation result to be stored.
	 * @param project The project.
	 * @return The ID of the data set.
	 * @throws BadDataConfigurationException If the number of attributes do not match with the stored data configuration.
	 * @throws BadDataSetIdException If the data set is already stored.
	 * @throws BadStateException If no file for the original data has been selected.
	 * @throws InternalDataSetPersistenceException If the data set could not be stored.
	 */
	@Transactional
	public Long storeOriginalTransformationResult(final TransformationResult transformationResult,
	                                              final ProjectEntity project)
			throws BadDataConfigurationException, BadDataSetIdException, BadStateException, InternalDataSetPersistenceException {
		// Delete the existing data set
		deleteDataSetIfNotConfirmedOrThrow(project.getOriginalData().getDataSet());

		// Store configuration
		final DataSet dataSet = transformationResult.getDataSet();
		final DataSetEntity dataSetEntity = doStoreOriginalDataConfiguration(project, dataSet.getDataConfiguration());

		// Store transformation errors
		convertTransformationErrors(transformationResult, dataSetEntity);

		projectRepository.save(project);

		storeDataSet(dataSet, dataSetEntity);

		return dataSetEntity.getId();
	}

	/**
	 * Stores the given TransformationResult by storing the DataSet,
	 * the DataConfiguration and the transformation errors into the database
	 * and associates them with the given process.
	 * The table for the DataSet will be generated automatically.
	 *
	 * @param transformationResult TransformationResult to store.
	 * @param dataProcessingEntity The job that created the data set.
	 * @param processed            The steps that created the data set.
	 * @throws BadDataConfigurationException       If the data configuration is not valid.
	 * @throws BadDataSetIdException               If the data has already been confirmed.
	 * @throws BadStateException                   If the file for the dataset has not been selected.
	 * @throws InternalDataSetPersistenceException If the data set could not be stored due to an internal error.
	 */
	@Transactional
	public void storeTransformationResult(final TransformationResult transformationResult,
	                                      final DataProcessingEntity dataProcessingEntity,
	                                      final List<Job> processed)
			throws BadDataConfigurationException, BadDataSetIdException, BadStateException, InternalDataSetPersistenceException {
		// Delete the existing data set
		deleteDataSetIfNotConfirmedOrThrow(dataProcessingEntity.getDataSet());

		// Store configuration
		final ProjectEntity project = dataProcessingEntity.getExecutionStep().getPipeline().getProject();
		final DataSet dataSet = transformationResult.getDataSet();
		final DataSetEntity dataSetEntity = doStoreDataConfiguration(project, dataSet.getDataConfiguration(),
		                                                             dataProcessingEntity, processed);

		// Store transformation errors
		convertTransformationErrors(transformationResult, dataSetEntity);

		dataProcessingRepository.save(dataProcessingEntity);

		storeDataSet(dataSet, dataSetEntity);
	}

	/**
	 * Creates the hold-out split for the original data set.
	 * This is only possible if the data is stored and not confirmed.
	 *
	 * @param project           The project.
	 * @param holdOutPercentage The percentage of rows that should be added to the hol-out split. Must be between 0 and 1.
	 * @throws BadStateException                   If the state of the data forbids to create the hold-out split.
	 * @throws BadArgumentException                If the given percentage is invalid.
	 * @throws InternalDataSetPersistenceException If executing the queries failed.
	 */
	@Transactional
	public void createHoldOutSplit(final ProjectEntity project, final float holdOutPercentage)
			throws BadStateException, BadArgumentException, InternalDataSetPersistenceException {
		if (project.getOriginalData().getDataSet() == null || !project.getOriginalData().getDataSet().isStoredData()) {
			throw new BadStateException(BadStateException.NO_DATA_SET,
			                            "Creating the hold-out split requires the original date set to be stored!");
		}

		if (project.getOriginalData().getDataSet().isConfirmedData()) {
			throw new BadStateException(BadStateException.DATE_CONFIRMED,
			                            "Creating the hold-out split cannot be done after the data has been confirmed!");
		}

		if (holdOutPercentage < 0 || holdOutPercentage > 1) {
			throw new BadArgumentException(BadArgumentException.HOLD_OUT_PERCENTAGE,
			                               "Hold out percentage must be between 0 and 1!");
		}

		final String tableName = getTableName(project.getOriginalData().getDataSet().getId());

		// Reset existing hold-out split
		if (project.getOriginalData().isHasHoldOut()) {
			final String resetQuery =
					"""
					UPDATE %s
					SET %s = false;
					""".formatted(tableName, DataschemeGenerator.HOLD_OUT_FLAG_NAME);

			try {
				executeStatement(resetQuery);
			} catch (final SQLException e) {
				throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.HOLD_OUT, "Failed to reset the hold-out split!", e);
			}

			project.getOriginalData().setHasHoldOut(false);
		}

		projectRepository.save(project);

		// Set the seed
		final double seed = project.randomDouble(-1, 1);
		project.getOriginalData().setHoldOutSeed(seed);

		final String seedQuery = "SELECT setseed(%s);".formatted(Double.toString(seed));

		try {
			executeStatement(seedQuery);
		} catch (final SQLException e) {
			throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.HOLD_OUT, "Failed to set the seed!", e);
		}

		// Create new hold-out split
		final String query =
				"""
				WITH selected_rows AS (
				  SELECT ctid
				  FROM %s
				  ORDER BY random()
				  LIMIT (SELECT round(count(*) * %s) FROM %s)
				)
				UPDATE %s
				SET %s = true
				WHERE ctid IN (SELECT ctid FROM selected_rows);
				""".formatted(tableName, Float.toString(holdOutPercentage), tableName, tableName,
				              DataschemeGenerator.HOLD_OUT_FLAG_NAME);

		try {
			executeStatement(query);
		} catch (final SQLException e) {
			throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.HOLD_OUT, "Failed to create the hold-out split!", e);
		}

		project.getOriginalData().setHasHoldOut(true);
		project.getOriginalData().setHoldOutPercentage(holdOutPercentage);
		projectRepository.save(project);
	}

	/**
	 * Stores an arbitrary configuration under the given identifier.
	 * If a configuration with the given name is already present, it will be overwritten.
	 *
	 * @param configName    Identifier for the configuration.
	 * @param url           The URL for starting the process.
	 * @param configuration Configuration to store.
	 * @param project       The project the configuration should be associated with.
	 * @throws BadConfigurationNameException If the configuration name is not defined.
	 * @throws BadStateException             If the process is running or scheduled.
	 */
	@Transactional
	public void storeConfiguration(final String configName, @Nullable final String url,
	                               @Nullable final String configuration, final ProjectEntity project
	) throws BadStateException, BadConfigurationNameException {
		final var configDefinition = stepService.getExternalConfiguration(configName);
		ConfigurationListEntity configurationList = project.addConfigurationList(configDefinition);

		BackgroundProcessConfiguration config;
		if (configurationList.getConfigurations().isEmpty()) {
			config = new BackgroundProcessConfiguration();
			configurationList.getConfigurations().add(config);
			config.setConfigurationIndex(configurationList.getConfigurations().size() - 1);
		} else {
			config = configurationList.getConfigurations().get(0);

			for (final var usage: config.getUsages()) {

				if (usage.getExternalProcessStatus() == ProcessStatus.SCHEDULED ||
				    usage.getExternalProcessStatus() == ProcessStatus.RUNNING) {
					throw new BadStateException(BadStateException.PROCESS_STARTED,
					                            "Process cannot be configured if the it is scheduled or started!");
				}
			}
		}

		config.setProcessUrl(url);
		config.setConfiguration(configuration);

		projectRepository.save(project);
	}

	/**
	 * Returns the info objects of the data set associated with the given source in the given project.
	 *
	 * @param project       The project
	 * @param dataSetSource Source of the data set.
	 * @return The info object.
	 * @throws BadDataSetIdException                     If no dataset exists.
	 * @throws BadStateException                         If the data set does not exist.
	 * @throws BadStepNameException                      If the source is a job and the job does not exist or does not have a data set.
	 * @throws InternalApplicationConfigurationException If the process is not configured correctly
	 * @throws InternalDataSetPersistenceException       If the internal queries failed.
	 * @throws InternalInvalidStateException             If the application is in an invalid state.
	 * @throws InternalMissingHandlingException          If no handling exists for the selector of the process.
	 */
	public DataSetInfo getInfo(final ProjectEntity project,
	                           final DataSetSource dataSetSource)
			throws BadDataSetIdException, InternalDataSetPersistenceException, BadStepNameException, InternalApplicationConfigurationException, BadStateException, InternalInvalidStateException, InternalMissingHandlingException {
		final DataSetEntity dataSetEntity = dataSetService.getDataSetEntityOrThrow(project, dataSetSource);

		if (!dataSetEntity.isStoredData()) {
			return new DataSetInfo(0, 0, false, 0.0f);
		}

		final int rows = countEntries(dataSetEntity.getId());
		final int invalidRows = countInvalidRows(dataSetEntity.getId());

		boolean hasHoldOutSplit = false;
		float holdOutPercentage = 0.0f;
		final OriginalDataEntity originalData = dataSetEntity.getOriginalData();
		if (originalData != null) {
			hasHoldOutSplit = originalData.isHasHoldOut();
			holdOutPercentage = originalData.getHoldOutPercentage();
		}

		return new DataSetInfo(rows, invalidRows, hasHoldOutSplit, holdOutPercentage);
	}

	/**
	 * Exports the configuration of the data set associated with the given project and source.
	 *
	 * @param project       The project of which the configuration should be exported.
	 * @param dataSetSource Source of the data set.
	 * @return The configuration.
	 * @throws BadDataSetIdException                     If no DataConfiguration is associated with the given project.
	 * @throws BadStateException                         If the data set does not exist.
	 * @throws BadStepNameException                      If the source is a job and the job does not exist or does not have a data set.
	 * @throws InternalApplicationConfigurationException If the process is not configured correctly
	 * @throws InternalInvalidStateException             If the application is in an invalid state.
	 * @throws InternalIOException                       If the DataConfiguration could not be deserialized from the stored JSON.
	 * @throws InternalMissingHandlingException          If no handling exists for the selector of the process.
	 */
	@Transactional
	public DataConfiguration exportDataConfiguration(final ProjectEntity project, final DataSetSource dataSetSource)
			throws BadDataSetIdException, InternalIOException, BadStepNameException, InternalApplicationConfigurationException, BadStateException, InternalInvalidStateException, InternalMissingHandlingException {
		final DataSetEntity dataSetEntity = dataSetService.getDataSetEntityOrThrow(project, dataSetSource);
		return getDetachedDataConfiguration(dataSetEntity);
	}

	/**
	 * Exports the data set associated with the given project.
	 *
	 * @param project         The project of which the data set should be exported.
	 * @param holdOutSelector Which hold-out rows should be selected.
	 * @param dataSetSource   Source of the data set.
	 * @return The DataSet.
	 * @throws BadDataSetIdException                     If no DataConfiguration is associated with the given project.
	 * @throws BadStateException                         If the data set does not exist.
	 * @throws BadStepNameException                      If the source is a job and the job does not exist or does not have a data set.
	 * @throws InternalApplicationConfigurationException If the process is not configured correctly
	 * @throws InternalDataSetPersistenceException       If the data set could not be exported due to an internal error.
	 * @throws InternalInvalidStateException             If the application is in an invalid state.
	 * @throws InternalIOException                       If the DataConfiguration could not be deserialized from the stored JSON.
	 * @throws InternalMissingHandlingException          If no handling exists for the selector of the process.
	 */
	@Transactional
	public DataSet exportDataSet(final ProjectEntity project, final HoldOutSelector holdOutSelector, final DataSetSource dataSetSource)
			throws InternalDataSetPersistenceException, BadDataSetIdException, InternalIOException, BadStepNameException, InternalApplicationConfigurationException, BadStateException, InternalInvalidStateException, InternalMissingHandlingException {
		try {
			return exportDataSet(project, new ArrayList<>(), holdOutSelector, dataSetSource);
		} catch (final BadColumnNameException e) {
			throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.DATA_SET_EXPORT,
			                                              "Failed to export the dataset due to an error in the column selection!",
			                                              e);
		}
	}

	/**
	 * Exports the data of the given DataSetEntity.
	 *
	 * @param dataSetEntity   The data set entity.
	 * @param holdOutSelector Which hold-out rows should be selected.
	 * @return The data of the data set.
	 * @throws InternalDataSetPersistenceException If the data could not be exported.
	 * @throws InternalIOException                 If the data configuration could not be loaded.
	 */
	@Transactional
	public DataSet exportDataSet(final DataSetEntity dataSetEntity, final HoldOutSelector holdOutSelector)
			throws InternalDataSetPersistenceException, InternalIOException {
		try {
			return exportDataSet(dataSetEntity, new ArrayList<>(), holdOutSelector);
		} catch (final BadColumnNameException e) {
			throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.DATA_SET_EXPORT,
			                                              "Failed to export the dataset due to an error in the column selection!",
			                                              e);
		}
	}

	/**
	 * Exports the data set associated with the given project and selector.
	 * Returns the columns with the given names in the given order.
	 * If no column names are provided, all columns are exported.
	 *
	 * @param project         The project of which the data set should be exported.
	 * @param columnNames     Names of the columns to export. If empty, all columns will be exported.
	 * @param holdOutSelector Which hold-out rows should be selected.
	 * @param dataSetSource   Source of the data set.
	 * @return The DataSet.
	 * @throws BadColumnNameException                    If the data set does not contain a column with the given names.
	 * @throws BadDataSetIdException                     If no DataConfiguration is associated with the given project.
	 * @throws BadStateException                         If the data set does not exist.
	 * @throws BadStepNameException                      If the source is a job and the job does not exist or does not have a data set.
	 * @throws InternalApplicationConfigurationException If the process is not configured correctly
	 * @throws InternalDataSetPersistenceException       If the data set could not be exported due to an internal error.
	 * @throws InternalIOException                       If the DataConfiguration could not be deserialized from the stored JSON.
	 * @throws InternalInvalidStateException             If the application is in an invalid state.
	 * @throws InternalMissingHandlingException          If no handling exists for the selector of the process.
	 */
	@Transactional
	public DataSet exportDataSet(final ProjectEntity project, final List<String> columnNames,
	                             final HoldOutSelector holdOutSelector, final DataSetSource dataSetSource)
			throws BadColumnNameException, BadDataSetIdException, InternalDataSetPersistenceException, InternalIOException, BadStepNameException, InternalApplicationConfigurationException, BadStateException, InternalInvalidStateException, InternalMissingHandlingException {
		final DataSetEntity dataSetEntity = dataSetService.getDataSetEntityOrThrow(project, dataSetSource);
		return exportDataSet(dataSetEntity, columnNames, holdOutSelector);
	}

	/**
	 * Exports the data of the given DataSetEntity.
	 * Returns the columns with the given names in the given order.
	 * If no column names are provided, all columns are exported.
	 *
	 * @param dataSetEntity   The data set entity.
	 * @param columnNames     Names of the columns to export. If empty, all columns will be exported.
	 * @param holdOutSelector Which hold-out rows should be selected.
	 * @return The DataSet.
	 * @throws BadColumnNameException              If the data set does not contain a column with the given names.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 * @throws InternalIOException                 If the DataConfiguration could not be deserialized from the stored JSON.
	 */
	@Transactional
	public DataSet exportDataSet(final DataSetEntity dataSetEntity, final List<String> columnNames,
	                             final HoldOutSelector holdOutSelector)
			throws BadColumnNameException, InternalDataSetPersistenceException, InternalIOException {
		return exportDataSet(dataSetEntity, RowSelector.ALL, columnNames, holdOutSelector, false, 0, 0, false);
	}

	/**
	 * Confirms the original data of the given project.
	 * After confirming, the data can not be overwritten, only be deleted.
	 *
	 * @param project The project.
	 * @throws BadDataSetIdException If no data set exists for the original data.
	 */
	@Transactional
	public void confirmDataSet(final ProjectEntity project) throws BadDataSetIdException {
		final var dataSet = getOriginalDataSetEntity(project);
		if (dataSet.isEmpty() || !dataSet.get().isStoredData()) {
			throw new BadDataSetIdException(BadDataSetIdException.NO_DATA_SET, "The data has not been stored!");
		}
		dataSet.get().setConfirmedData(true);
		projectRepository.save(project);
	}

	/**
	 * Exports the transformation result associated with the given project and source.
	 *
	 * @param project         The project of which the data set should be exported.
	 * @param holdOutSelector Which hold-out rows should be selected.
	 * @param dataSetSource   Source of the data set.
	 * @return The transformation result.
	 * @throws BadDataSetIdException                     If no DataConfiguration is associated with the given project.
	 * @throws BadStateException                         If the data set does not exist.
	 * @throws BadStepNameException                      If the source is a job and the job does not exist or does not have a data set.
	 * @throws InternalApplicationConfigurationException If the process is not configured correctly
	 * @throws InternalDataSetPersistenceException       If the data set could not be exported due to an internal error.
	 * @throws InternalIOException                       If the DataConfiguration could not be deserialized from the stored JSON.
	 * @throws InternalInvalidStateException             If the application is in an invalid state.
	 * @throws InternalMissingHandlingException          If no handling exists for the selector of the process.
	 */
	@Transactional
	public TransformationResult exportTransformationResult(final ProjectEntity project, final HoldOutSelector holdOutSelector, final DataSetSource dataSetSource)
			throws BadDataSetIdException, InternalDataSetPersistenceException, InternalIOException, BadStepNameException, InternalApplicationConfigurationException, BadStateException, InternalInvalidStateException, InternalMissingHandlingException {
		final DataSet dataSet = exportDataSet(project, holdOutSelector, dataSetSource);
		final DataSetEntity dataSetEntity = dataSetService.getDataSetEntityOrThrow(project, dataSetSource);

		final Map<Integer, DataRowTransformationError> rowErrors = new HashMap<>();
		for (final var error : dataSetEntity.getDataTransformationErrors()) {
			if (!rowErrors.containsKey(error.getRowIndex())) {
				rowErrors.put(error.getRowIndex(), new DataRowTransformationError(error.getRowIndex()));
			}
			final var rowError = rowErrors.get(error.getRowIndex());
			rowError.addError(new DataTransformationError(error.getColumnIndex(), error.getErrorType(),
			                                              error.getOriginalValue()));
		}

		return new TransformationResult(dataSet, rowErrors.values().stream().toList());
	}

	/**
	 * Exports a page of the transformation result associated with the given step in the given project.
	 * Starts at the given page number taking the given page size into account.
	 * Returns the columns with the given names in the given order.
	 * If no column names are provided, all columns are exported.
	 * Includes only the rows that macht the given row selector.
	 * Encodes the data as specified in the given LoadDataRequest.
	 *
	 * @param dataSetEntity   The data set to be exported form.
	 * @param rowSelector     Selector specifying which rows should be included.
	 * @param pageNumber      The number of the page to be exported.
	 * @param pageSize        The number of items per page.
	 * @param loadDataRequest Export settings.
	 * @return The page containing the data and meta-data about the page.
	 * @throws BadColumnNameException              If the data set does not contain a column with the given names.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 * @throws InternalIOException                 If the DataConfiguration could not be deserialized from the stored JSON.
	 */
	@Transactional
	public TransformationResultPage exportTransformationResultPage(final DataSetEntity dataSetEntity,
	                                                               final RowSelector rowSelector,
	                                                               final int pageNumber,
	                                                               final int pageSize,
	                                                               final LoadDataRequest loadDataRequest)
			throws InternalDataSetPersistenceException, BadColumnNameException, InternalIOException {
		final List<String> columnNames = loadDataRequest != null ? loadDataRequest.getColumnNames() : new ArrayList<>();

		var calcRowNumbers = rowSelector != RowSelector.ALL;

		final var startRow = (pageNumber - 1) * pageSize;
		final var endRow = startRow + pageSize;

		final Map<Integer, Integer> columnIndexMapping = dataSetService.getColumnIndexMapping(dataSetEntity.getDataConfiguration(), columnNames);
		final DataSet dataSet = exportDataSet(dataSetEntity, rowSelector, columnNames,
		                                      loadDataRequest.getHoldOutSelector(), true, startRow, pageSize, calcRowNumbers);

		List<Integer> rowNumbers = null;
		final Set<DataTransformationErrorEntity> errors;
		if (calcRowNumbers) {
			rowNumbers = dataSet.getData().stream().map(a -> (Integer) a.get(a.size() - 1)).toList();
			errors = errorRepository.findByDataSetIdAndRowIndexIn(dataSetEntity.getId(), rowNumbers);
		} else {
			errors = errorRepository.findByDataSetIdAndRowIndexBetween(dataSetEntity.getId(), startRow, endRow - 1);
		}

		List<List<Object>> data = dataSetService.encodeDataRows(dataSet, errors, startRow, rowNumbers, columnIndexMapping,
		                                                        loadDataRequest);

		if (calcRowNumbers) {
			data = data.stream().map(a -> a.subList(0, a.size() - 1)).toList();
		}

		final int numberRows = countEntries(dataSetEntity.getId(), loadDataRequest.getHoldOutSelector(), rowSelector);
		final int numberPages = (int) Math.ceil((float) numberRows / pageSize);

		final Map<Integer, DataRowTransformationError> rowErrors = new HashMap<>();
		for (final var error : errors) {
			if (!columnIndexMapping.containsKey(error.getColumnIndex())) {
				continue;
			}

			if (!rowErrors.containsKey(error.getRowIndex())) {
				final int index = rowNumbers != null ? rowNumbers.indexOf(error.getRowIndex()) : error.getRowIndex() - startRow;
				rowErrors.put(error.getRowIndex(), new DataRowTransformationError(index));
			}
			final var rowError = rowErrors.get(error.getRowIndex());
			final Integer columnIndex = columnIndexMapping.get(error.getColumnIndex());
			rowError.addError(new DataTransformationError(columnIndex, error.getErrorType(), error.getOriginalValue()));
		}

		final List<DataRowTransformationError> transformationErrors = rowErrors.values().stream().toList();

		return new TransformationResultPage(data, transformationErrors, rowNumbers, pageNumber, pageSize, numberRows,
		                                    numberPages);
	}

	/**
	 * Exports the configuration with the given name
	 *
	 * @param configurationName Name of the configuration to export.
	 * @param project           The project of which the configuration should be exported.
	 * @return The configuration.
	 * @throws BadConfigurationNameException If the project does not have a configuration with the given name.
	 */
	@Transactional
	@Nullable
	public String exportConfiguration(final String configurationName, final ProjectEntity project)
			throws BadConfigurationNameException {
		final var config = stepService.getExternalConfiguration(configurationName);
		final var configList = project.getConfigurationList(config);

		if (configList == null || configList.getConfigurations().isEmpty()) {
			throw new BadConfigurationNameException(BadConfigurationNameException.NO_CONFIGURATION,
			                                        "No configuration in project '" + project.getId() +
			                                        "' for name '" + configurationName + "' found!");
		}

		return configList.getConfigurations().get(0).getConfiguration();
	}

	/**
	 * Removes the DataSet and the transformation errors associated with the given project from the database
	 * and deletes the corresponding table.
	 *
	 * @param project The project of which the data set should be deleted.
	 * @throws BadDataSetIdException               If no data set is associated with the given project.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 */
	@Transactional
	public void delete(final ProjectEntity project)
			throws BadDataSetIdException, InternalDataSetPersistenceException {
		project.getOriginalData().setFile(null);
		deleteDataSet(project.getOriginalData().getDataSet());

		for (final var pipeline : project.getPipelines()) {
			for (final var stage : pipeline.getStages()) {
				for (final var job : stage.getProcesses()) {
					job.setStatus(null);
					job.setExternalProcessStatus(ProcessStatus.NOT_STARTED);
					job.setScheduledTime(null);
					job.setConfiguration(null);
					job.getResultFiles().clear();

					if (job instanceof DataProcessingEntity dataProcessing) {
						deleteDataSet(dataProcessing.getDataSet());
					}
				}

				stage.setStatus(ProcessStatus.NOT_STARTED);
			}
		}

		projectRepository.save(project);
	}

	/**
	 * Counts the number of rows in the dataset with the given ID.
	 *
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
	 * Counts the number of entries in the data set that comply the given selectors.
	 *
	 * @param dataSetId       The ID of the data set.
	 * @param holdOutSelector Which hold-out rows should be selected.
	 * @param rowSelector     Selector specifying which rows should be included regarding on the hold-out split.
	 * @return The number of entries.
	 * @throws InternalDataSetPersistenceException If the number could not be retrieved.
	 */
	public int countEntries(final long dataSetId, final HoldOutSelector holdOutSelector, final RowSelector rowSelector) throws InternalDataSetPersistenceException {
		String countQuery = "SELECT count(*) FROM " + getTableName(dataSetId) + " as d ";
		countQuery = appendHoldOutCondition(countQuery, holdOutSelector);
		countQuery = appendRowSelectorCondition(countQuery, rowSelector, dataSetId);
		countQuery += ";";

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
	 *
	 * @param dataSetId The ID of the dataset.
	 * @return The number of invalid rows.
	 */
	public int countInvalidRows(final long dataSetId) {
		return (int) errorRepository.countDistinctRowIndexByDataSetId(dataSetId);
	}

	/**
	 * Checks if a table for the data set with the given ID exists.
	 *
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
	 *
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

	private Optional<DataSetEntity> getOriginalDataSetEntity(final ProjectEntity project) {
		return Optional.ofNullable(project.getOriginalData().getDataSet());
	}

	private DataSetEntity doStoreOriginalDataConfiguration(final ProjectEntity project,
	                                                       final DataConfiguration dataConfiguration)
			throws BadDataConfigurationException, BadStateException {
		checkFile(project, dataConfiguration);

		final DataSetEntity dataSetEntity;

		if (project.getOriginalData().getDataSet() == null) {
			dataSetEntity = new DataSetEntity(project.getOriginalData());
		} else {
			dataSetEntity = project.getOriginalData().getDataSet();
		}

		dataSetEntity.setDataConfiguration(dataConfiguration);

		projectRepository.save(project);

		return dataSetEntity;
	}

	private DataSetEntity doStoreDataConfiguration(final ProjectEntity project,
	                                               final DataConfiguration dataConfiguration,
	                                               final DataProcessingEntity dataProcessingEntity,
	                                               final List<Job> processed
	) throws BadDataConfigurationException, BadStateException {
		checkFile(project, dataConfiguration);

		final DataSetEntity dataSetEntity;

		if (dataProcessingEntity.getDataSet() == null) {
			dataSetEntity = new DataSetEntity(dataProcessingEntity);
		} else {
			dataSetEntity = dataProcessingEntity.getDataSet();
		}

		dataSetEntity.setDataConfiguration(dataConfiguration);
		dataSetEntity.setProcessed(processed);

		projectRepository.save(project);

		return dataSetEntity;
	}

	private void checkFile(final ProjectEntity project, final DataConfiguration dataConfiguration
	) throws BadStateException, BadDataConfigurationException {
		final FileEntity file = project.getOriginalData().getFile();
		if (file == null) {
			throw new BadStateException(BadStateException.NO_DATASET_FILE,
			                            "Saving a data configuration requires the file for the dataset to be selected.");
		}

		if (dataConfiguration.getConfigurations().size() != file.getNumberOfAttributes()) {
			throw new BadDataConfigurationException(BadDataConfigurationException.INVALID_NUMBER_OF_ATTRIBUTES,
			                                        "Dataset contains " + file.getNumberOfAttributes() +
			                                        " attributes, but the data configuration " +
			                                        dataConfiguration.getConfigurations().size() + " attributes!");
		}
	}

	private void storeDataSet(final DataSet dataSet, final DataSetEntity dataSetEntity)
			throws BadDataConfigurationException, InternalDataSetPersistenceException {
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
			long rowNumber = 0;
			for (final DataRow dataRow : dataSet.getDataRows()) {
				final List<String> stringRow = new ArrayList<>();

				// Add values from the data set
				for (final Data data : dataRow.getData()) {
					stringRow.add(convertDataToString(data));
				}

				// Add initial value for is_hold_out flag
				stringRow.add(Boolean.FALSE.toString());

				// Add row number for row_number
				stringRow.add(String.valueOf(rowNumber));

				String values = String.join(",", stringRow);

				insertStatement.execute("INSERT INTO " + tableName + " VALUES (" + values + ")");

				rowNumber++;
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
		dataSetRepository.save(dataSetEntity);
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

	private DataSet exportDataSet(final DataSetEntity dataSetEntity, final RowSelector rowSelector,
	                              List<String> columnNames, final HoldOutSelector holdOutSelector,
	                              final boolean pagination, final int startRow, final int pageSize,
	                              final boolean exportRowIndexColumn)
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

			final String exportQuery = createSelectQuery(dataSetEntity.getId(), rowSelector, columnNames,
			                                             holdOutSelector, pagination, startRow, pageSize,
			                                             exportRowIndexColumn);

			try (final ResultSet resultSet = exportStatement.executeQuery(exportQuery)) {
				while (resultSet.next()) {
					final List<Data> data = new ArrayList<>();
					for (int columnIndex = 0;
					     columnIndex < dataConfiguration.getConfigurations().size(); ++columnIndex) {
						final ColumnConfiguration columnConfiguration = dataConfiguration.getConfigurations()
						                                                                 .get(columnIndex);
						data.add(convertResultToData(resultSet, columnIndex + 1, columnConfiguration.getType()));
					}

					if (exportRowIndexColumn) {
						data.add(convertResultToData(resultSet, dataConfiguration.getConfigurations().size() + 1, DataType.INTEGER));
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

	private String createSelectQuery(final Long dataSetId, final RowSelector rowSelector, final List<String> columnNames,
	                                 final HoldOutSelector holdOutSelector, final boolean pagination,
	                                 final int startRow, final int pageSize, final boolean exportRowIndexColumn) {
		final List<String> quotedColumnNames = columnNames.stream().map(it -> "\"" + it + "\"")
		                                                  .collect(Collectors.toCollection(ArrayList::new));
		if (exportRowIndexColumn) {
			quotedColumnNames.add("\"" + DataschemeGenerator.ROW_INDEX_NAME + "\"");
		}

		String query = "SELECT " + String.join(",", quotedColumnNames) + " FROM " + getTableName(dataSetId) + " d";
		query = appendHoldOutCondition(query, holdOutSelector);
		query = appendRowSelectorCondition(query, rowSelector, dataSetId);

		query += " ORDER BY " + DataschemeGenerator.ROW_INDEX_NAME + " ASC";
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
					var a = resultSet.getObject(columnIndex);
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

			final ColumnConfiguration columnConfiguration = sourceConfiguration.getColumnConfigurationByColumnName(
					columnName);

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

	private void deleteDataSet(@Nullable final DataSetEntity dataSet) throws InternalDataSetPersistenceException {
		if (dataSet == null) {
			return;
		}

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
		dataSet.setConfirmedData(false);
		dataSet.getStatisticsProcess().reset();

		final OriginalDataEntity original = dataSet.getOriginalData();
		if (original != null) {
			original.setHasHoldOut(false);
		}

		projectRepository.save(dataSet.getProject());
	}

	/**
	 * Checks if the data set for the given step has been confirmed.
	 * Otherwise, deletes the data set if present.
	 *
	 * @param dataSet The data set to be deleted.
	 * @throws BadDataSetIdException               If the data is confirmed.
	 * @throws InternalDataSetPersistenceException If the data set could not be deleted.
	 */
	private void deleteDataSetIfNotConfirmedOrThrow(
			@Nullable final DataSetEntity dataSet
	) throws BadDataSetIdException, InternalDataSetPersistenceException {
		if (dataSet == null) {
			return;
		}

		if (dataSet.isConfirmedData()) {
			throw new BadDataSetIdException(BadDataSetIdException.ALREADY_STORED, "The data has already been stored!");
		} else if (dataSet.isStoredData()) {
			deleteDataSet(dataSet);
		}
	}

	private void convertTransformationErrors(final TransformationResult transformationResult,
	                                         final DataSetEntity dataSet) {
		for (final DataRowTransformationError rowTransformationError : transformationResult.getTransformationErrors()) {
			for (final DataTransformationError transformationError : rowTransformationError.getDataTransformationErrors()) {

				final DataTransformationErrorEntity transformationErrorEntity = new DataTransformationErrorEntity();

				transformationErrorEntity.setRowIndex(rowTransformationError.getIndex());
				transformationErrorEntity.setColumnIndex(transformationError.getIndex());
				transformationErrorEntity.setErrorType(transformationError.getErrorType());
				transformationErrorEntity.setOriginalValue(transformationError.getRawValue());

				dataSet.addDataRowTransformationError(transformationErrorEntity);
			}
		}
	}

	private String appendHoldOutCondition(String query, final HoldOutSelector holdOutSelector) {
		switch (holdOutSelector) {
			case ALL -> {
			}
			case HOLD_OUT -> {
				query = appendWhere(query);
				query += DataschemeGenerator.HOLD_OUT_FLAG_NAME + " = true";
			}
			case NOT_HOLD_OUT -> {
				query = appendWhere(query);
				query += DataschemeGenerator.HOLD_OUT_FLAG_NAME + " = false";
			}
		}

		return query;
	}

	private String appendRowSelectorCondition(String query, final RowSelector rowSelector, final Long dataSetId) {
		switch (rowSelector) {
			case ALL -> {}
			case VALID -> {
				query = appendWhere(query);
				query += "NOT EXISTS (SELECT 1 FROM data_transformation_error_entity e WHERE e.data_set_id = " + dataSetId + " AND e.row_index = d." + DataschemeGenerator.ROW_INDEX_NAME + ")";
			}
			case ERRORS -> {
				query = appendWhere(query);
				query += "EXISTS (SELECT 1 FROM data_transformation_error_entity e WHERE e.data_set_id = " + dataSetId + " AND e.row_index = d." + DataschemeGenerator.ROW_INDEX_NAME + ")";
			}
		}

		return query;
	}

	private String appendWhere(final String query) {
		if (query.contains("WHERE")) {
			return query + " AND ";
		} else {
			return query + " WHERE ";
		}
	}

}
