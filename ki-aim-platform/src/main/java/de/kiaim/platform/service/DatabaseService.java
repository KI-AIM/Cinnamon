package de.kiaim.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.configuration.data.ColumnConfiguration;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.model.data.*;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.platform.config.KiAimConfiguration;
import de.kiaim.platform.config.SerializationConfig;
import de.kiaim.platform.exception.*;
import de.kiaim.platform.helper.DataschemeGenerator;
import de.kiaim.platform.model.DataRowTransformationError;
import de.kiaim.platform.model.DataTransformationError;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.dto.DataSetInfo;
import de.kiaim.platform.model.dto.FileInformation;
import de.kiaim.platform.model.dto.TransformationResultPage;
import de.kiaim.platform.model.dto.LoadDataRequest;
import de.kiaim.platform.model.entity.*;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.RowSelector;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.model.file.FileConfiguration;
import de.kiaim.platform.processor.DataProcessor;
import de.kiaim.platform.repository.*;
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
import java.util.stream.IntStream;

@Service
public class DatabaseService {

	private final Logger LOGGER = LoggerFactory.getLogger(DatabaseService.class);

	private final KiAimConfiguration kiAimConfiguration;

	private final Connection connection;
	private final ExternalProcessRepository externalProcessRepository;
	private final DataProcessingRepository dataProcessingRepository;
	private final DataSetRepository dataSetRepository;
	private final DataTransformationErrorRepository errorRepository;
	private final ProjectRepository projectRepository;

	private final DataschemeGenerator dataschemeGenerator;
	private final ObjectMapper jsonMapper;

	private final DataSetService dataSetService;
	private final DataProcessorService dataProcessorService;

	@Autowired
	public DatabaseService(final KiAimConfiguration kiAimConfiguration, final DataSource dataSource,
	                       final DataProcessingRepository dataProcessingRepository,
	                       final DataTransformationErrorRepository errorRepository,
	                       final ExternalProcessRepository externalProcessRepository,
	                       final SerializationConfig serializationConfig, final DataSetRepository dataSetRepository,
	                       final ProjectRepository projectRepository, final DataschemeGenerator dataschemeGenerator,
	                       final DataSetService dataSetService, final DataProcessorService dataProcessorService) {
		this.kiAimConfiguration = kiAimConfiguration;
		this.connection = DataSourceUtils.getConnection(dataSource);
		this.dataProcessingRepository = dataProcessingRepository;
		this.errorRepository = errorRepository;
		jsonMapper = serializationConfig.jsonMapper();
		this.dataSetRepository = dataSetRepository;
		this.projectRepository = projectRepository;
		this.dataschemeGenerator = dataschemeGenerator;
		this.dataSetService = dataSetService;
		this.dataProcessorService = dataProcessorService;
		this.externalProcessRepository = externalProcessRepository;
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
	 * @throws BadDataConfigurationException       If the data configuration is not valid.
	 * @throws BadDataSetIdException               If the data has already been confirmed.
	 * @throws BadStateException                   If the file for the dataset has not been selected.
	 * @throws InternalDataSetPersistenceException If the data set could not be stored due to an internal error.
	 */
	@Transactional
	public void storeTransformationResult(final TransformationResult transformationResult,
	                                      final DataProcessingEntity dataProcessingEntity)
			throws BadDataConfigurationException, BadDataSetIdException, BadStateException, InternalDataSetPersistenceException {
		// Delete the existing data set
		deleteDataSetIfNotConfirmedOrThrow(dataProcessingEntity.getDataSet());

		// Store configuration
		final ProjectEntity project = dataProcessingEntity.getExecutionStep().getPipeline().getProject();
		final DataSet dataSet = transformationResult.getDataSet();
		final DataSetEntity dataSetEntity = doStoreDataConfiguration(project, dataSet.getDataConfiguration(),
		                                                             dataProcessingEntity);

		// Store transformation errors
		convertTransformationErrors(transformationResult, dataSetEntity);

		dataProcessingRepository.save(dataProcessingEntity);

		storeDataSet(dataSet, dataSetEntity);
	}

	/**
	 * Stores the given algorithm configuration
	 *
	 * @param configuration   The algorithm configuration to be stored.
	 * @param externalProcess The process the configuration should be associated with.
	 */
	@Transactional
	public void storeConfiguration(final String configuration, final ExternalProcessEntity externalProcess) {
		externalProcess.setConfiguration(configuration);
		externalProcessRepository.save(externalProcess);
	}

	/**
	 * Stores an arbitrary configuration under the given identifier.
	 * If a configuration with the given name is already present, it will be overwritten.
	 *
	 * @param configurationName Identifier for the configuration.
	 * @param configuration     Configuration to store.
	 * @param project           The project the configuration should be associated with.
	 */
	@Transactional
	public void storeConfiguration(final String configurationName, final String configuration,
	                               final ProjectEntity project) throws BadStepNameException, BadConfigurationNameException {
		final ExternalProcessEntity process = getExternalProcessForConfigurationName(project, configurationName);
		this.storeConfiguration(configuration, process);
	}

	/**
	 * Returns the process for the step that configuration name matches the given name.
	 *
	 * @param project           The project.
	 * @param configurationName The configured configuration name.
	 * @return The process.
	 * @throws BadConfigurationNameException If the configuration name is not defined.
	 * @throws BadStepNameException          If the step does not exist.
	 */
	public ExternalProcessEntity getExternalProcessForConfigurationName(final ProjectEntity project,
	                                                                    final String configurationName)
			throws BadConfigurationNameException, BadStepNameException {
		// Search for the step that has the given configuraitonName
		String stepName = null;
		for (final var entry : kiAimConfiguration.getSteps().entrySet()) {
			if (entry.getValue().getConfigurationName().equals(configurationName)) {
				stepName = entry.getKey();
				break;
			}
		}

		if (stepName == null) {
			throw new BadConfigurationNameException(BadConfigurationNameException.NOT_FOUND,
			                                        "Project with ID '" + project.getId() +
			                                        "' has no configuration with the name '" + configurationName +
			                                        "'!");
		}

		final Step processStep = Step.getStepOrThrow(stepName);

		// Get the execution for the found step
		Step exectionStep = null;
		for (final var step1 : Step.values()) {
			if (step1.getProcesses().contains(processStep)) {
				exectionStep = step1;
			}
		}

		if (exectionStep == null) {
			throw new BadConfigurationNameException(BadConfigurationNameException.NOT_FOUND,
			                                        "Project with ID '" + project.getId() +
			                                        "' has no configuration with the name '" + configurationName +
			                                        "'!");
		}

		final ExecutionStepEntity executionStep = project.getPipelines().get(0).getStageByStep(exectionStep);
		return executionStep.getProcesses().get(processStep);
	}

	/**
	 * Returns the info objects of the data set associated with the given step in the given project.
	 *
	 * @param project The project
	 * @param step    The step the data sets is associated with.
	 * @return The info object.
	 * @throws BadDataSetIdException               If no dataset exists.
	 * @throws InternalDataSetPersistenceException If the internal queries failed.
	 */
	public DataSetInfo getInfo(final ProjectEntity project,
	                           final Step step) throws BadDataSetIdException, InternalDataSetPersistenceException {
		final DataSetEntity dataSetEntity = dataSetService.getDataSetEntityOrThrow(project, step);
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
	 * @throws InternalIOException   If the DataConfiguration could not be deserialized from the stored JSON.
	 */
	@Transactional
	public DataConfiguration exportDataConfiguration(final ProjectEntity project, final Step step)
			throws BadDataSetIdException, InternalIOException {
		final DataSetEntity dataSetEntity = dataSetService.getDataSetEntityOrThrow(project, step);
		return getDetachedDataConfiguration(dataSetEntity);
	}

	/**
	 * Exports the data set associated with the given project and step.
	 *
	 * @param project The project of which the data set should be exported.
	 * @param step    The step of which the data set should be exported.
	 * @return The DataSet.
	 * @throws BadDataSetIdException               If no DataConfiguration is associated with the given project.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 * @throws InternalIOException                 If the DataConfiguration could not be deserialized from the stored JSON.
	 */
	@Transactional
	public DataSet exportDataSet(final ProjectEntity project, final Step step)
			throws InternalDataSetPersistenceException, BadDataSetIdException, InternalIOException {
		try {
			return exportDataSet(project, new ArrayList<>(), step);
		} catch (final BadColumnNameException e) {
			throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.DATA_SET_EXPORT,
			                                              "Failed to export the dataset due to an error in the column selection!",
			                                              e);
		}
	}

	/**
	 * Exports the data of the given DataSetEntity.
	 * @param dataSetEntity The data set entity.
	 * @return The data of the data set.
	 * @throws InternalDataSetPersistenceException If the data could not be exported.
	 * @throws InternalIOException                 If the data configuration could not be loaded.
	 */
	@Transactional
	public DataSet exportDataSet(final DataSetEntity dataSetEntity)
			throws InternalDataSetPersistenceException, InternalIOException {
		try {
			return exportDataSet(dataSetEntity, new ArrayList<>());
		} catch (final BadColumnNameException e) {
			throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.DATA_SET_EXPORT,
			                                              "Failed to export the dataset due to an error in the column selection!",
			                                              e);
		}
	}

	/**
	 * Exports the data set associated with the given project and step.
	 * Returns the columns with the given names in the given order.
	 * If no column names are provided, all columns are exported.
	 *
	 * @param project     The project of which the data set should be exported.
	 * @param columnNames Names of the columns to export. If empty, all columns will be exported.
	 * @param step        The step of which the data set should be exported.
	 * @return The DataSet.
	 * @throws BadColumnNameException              If the data set does not contain a column with the given names.
	 * @throws BadDataSetIdException               If no DataConfiguration is associated with the given project.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 * @throws InternalIOException                 If the DataConfiguration could not be deserialized from the stored JSON.
	 */
	@Transactional
	public DataSet exportDataSet(final ProjectEntity project, final List<String> columnNames, final Step step)
			throws BadColumnNameException, BadDataSetIdException, InternalDataSetPersistenceException, InternalIOException {
		final DataSetEntity dataSetEntity = dataSetService.getDataSetEntityOrThrow(project, step);
		return exportDataSet(dataSetEntity, columnNames);
	}

	/**
	 * Exports the data of the given DataSetEntity.
	 * Returns the columns with the given names in the given order.
	 * If no column names are provided, all columns are exported.
	 *
	 * @param dataSetEntity The data set entity.
	 * @param columnNames   Names of the columns to export. If empty, all columns will be exported.
	 * @return The DataSet.
	 * @throws BadColumnNameException              If the data set does not contain a column with the given names.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 * @throws InternalIOException                 If the DataConfiguration could not be deserialized from the stored JSON.
	 */
	@Transactional
	public DataSet exportDataSet(final DataSetEntity dataSetEntity, final List<String> columnNames)
			throws BadColumnNameException, InternalDataSetPersistenceException, InternalIOException {
		return exportDataSet(dataSetEntity, columnNames, null, false, 0, 0);
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
	 * Exports the transformation result associated with the given project and step.
	 *
	 * @param project The project of which the data set should be exported.
	 * @param step    The step of which the data set should be exported.
	 * @return The transformation result.
	 * @throws BadDataSetIdException               If no DataConfiguration is associated with the given project.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 * @throws InternalIOException                 If the DataConfiguration could not be deserialized from the stored JSON.
	 */
	@Transactional
	public TransformationResult exportTransformationResult(final ProjectEntity project, final Step step)
			throws BadDataSetIdException, InternalDataSetPersistenceException, InternalIOException {
		final DataSet dataSet = exportDataSet(project, step);
		final DataSetEntity dataSetEntity = dataSetService.getDataSetEntityOrThrow(project, step);

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
	 * Exports a page of the transformation result associated with the given project and step.
	 * Returns the columns with the given names in the given order.
	 * If no column names are provided, all columns are exported.
	 * Includes only the rows that macht the given row selector.
	 * Encodes the data as specified in the given LoadDataRequest.
	 *
	 * @param project         The project of which the data set should be exported.
	 * @param step            The step of which the data set should be exported.
	 * @param columnNames     Names of the columns to export. If empty, all columns will be exported.
	 * @param pageNumber      Number of the page to be exported.
	 * @param pageSize        Number of entries per page.
	 * @param rowSelector     Selector specifying which rows should be included.
	 * @param loadDataRequest Export settings.
	 * @return The page of the transformation result.
	 * @throws BadColumnNameException              If the data set does not contain a column with the given names.
	 * @throws BadDataSetIdException               If no DataConfiguration is associated with the given project.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 * @throws InternalIOException                 If the DataConfiguration could not be deserialized from the stored JSON.
	 */
	@Transactional
	public TransformationResultPage exportTransformationResultPage(final ProjectEntity project, final Step step,
	                                                               final List<String> columnNames, final int pageNumber,
	                                                               final int pageSize, final RowSelector rowSelector,
	                                                               final LoadDataRequest loadDataRequest)
			throws BadColumnNameException, BadDataSetIdException, InternalDataSetPersistenceException, InternalIOException {
		final DataSetEntity dataSetEntity = dataSetService.getDataSetEntityOrThrow(project, step);

		final var startRow = (pageNumber - 1) * pageSize;
		final var endRow = startRow + pageSize;

		final int numberTotalRows = countEntries(dataSetEntity.getId());
		final int numberInvalidRows = countInvalidRows(dataSetEntity.getId());

		List<Integer> rowNumbers;
		final int numberRows;
		if (rowSelector == RowSelector.VALID) {
			final List<Integer> invalid = errorRepository.findRowIndexByDataSetIdOrderByRowIndexAsc(
					dataSetEntity.getId());
			rowNumbers = IntStream.rangeClosed(0, numberTotalRows).boxed().collect(Collectors.toList());
			rowNumbers.removeAll(invalid);
			rowNumbers = rowNumbers.subList(startRow, endRow);

			numberRows = numberTotalRows - numberInvalidRows;
		} else {
			rowNumbers = errorRepository.findRowIndexByDataSetIdOrderByRowIndexAsc(dataSetEntity.getId(), pageSize,
			                                                                       startRow);
			numberRows = numberInvalidRows;
		}

		final int numberPages = (int) Math.ceil((float) numberRows / pageSize);

		final DataSet dataSet = exportDataSet(dataSetEntity, columnNames, rowNumbers, false, 0, 0);
		final Set<DataTransformationErrorEntity> errors2 = errorRepository.findByDataSetIdAndRowIndexIn(
				dataSetEntity.getId(), rowNumbers);

		final List<List<Object>> data = dataSetService.encodeDataRows(dataSet, errors2, 0, rowNumbers, loadDataRequest);

		final Map<Integer, DataRowTransformationError> rowErrors = new HashMap<>();
		for (final var error : errors2) {
			if (!rowErrors.containsKey(error.getRowIndex())) {
				rowErrors.put(error.getRowIndex(),
				              new DataRowTransformationError(rowNumbers.indexOf(error.getRowIndex())));
			}
			final var rowError = rowErrors.get(error.getRowIndex());
			rowError.addError(new DataTransformationError(error.getColumnIndex(), error.getErrorType(),
			                                              error.getOriginalValue()));
		}

		final List<DataRowTransformationError> transformationErrors = rowErrors.values().stream().toList();

		return new TransformationResultPage(data, transformationErrors, rowNumbers, pageNumber, pageSize, numberRows,
		                                    numberPages);
	}

	/**
	 * Exports a page of the transformation result associated with the given step in the given project.
	 * Starts at the given page number taking the given page size into account.
	 * Returns the columns with the given names in the given order.
	 * If no column names are provided, all columns are exported.
	 *
	 * @param project         The project of which the data set should be exported.
	 * @param columnNames     Names of the columns to export. If empty, all columns will be exported.
	 * @param step            The step of which the data should be exported.
	 * @param pageNumber      The number of the page to be exported.
	 * @param pageSize        The number of items per page.
	 * @param loadDataRequest Export settings.
	 * @return The page containing the data and meta-data about the page.
	 * @throws BadColumnNameException              If the data set does not contain a column with the given names.
	 * @throws BadDataSetIdException               If no DataConfiguration is associated with the given project.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 * @throws InternalIOException                 If the DataConfiguration could not be deserialized from the stored JSON.
	 */
	@Transactional
	public TransformationResultPage exportTransformationResultPage(final ProjectEntity project, final Step step,
	                                                               final List<String> columnNames, final int pageNumber,
	                                                               final int pageSize,
	                                                               final LoadDataRequest loadDataRequest)
			throws BadDataSetIdException, InternalDataSetPersistenceException, BadColumnNameException, InternalIOException {
		final DataSetEntity dataSetEntity = dataSetService.getDataSetEntityOrThrow(project, step);

		final var startRow = (pageNumber - 1) * pageSize;
		final var endRow = startRow + pageSize;

		final DataSet dataSet = exportDataSet(dataSetEntity, columnNames, null, true, startRow, pageSize);
		final Set<DataTransformationErrorEntity> errors = errorRepository.findByDataSetIdAndRowIndexBetween(
				dataSetEntity.getId(), startRow, endRow - 1);

		final List<List<Object>> data = dataSetService.encodeDataRows(dataSet, errors, startRow, null, loadDataRequest);
		final int numberRows = countEntries(dataSetEntity.getId());
		final int numberPages = (int) Math.ceil((float) numberRows / pageSize);

		final Map<Integer, DataRowTransformationError> rowErrors = new HashMap<>();
		for (final var error : errors) {
			if (!rowErrors.containsKey(error.getRowIndex())) {
				rowErrors.put(error.getRowIndex(), new DataRowTransformationError(error.getRowIndex() - startRow));
			}
			final var rowError = rowErrors.get(error.getRowIndex());
			rowError.addError(new DataTransformationError(error.getColumnIndex(), error.getErrorType(),
			                                              error.getOriginalValue()));
		}

		final List<DataRowTransformationError> transformationErrors = rowErrors.values().stream().toList();

		return new TransformationResultPage(data, transformationErrors, null, pageNumber, pageSize, numberRows,
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
			throws BadConfigurationNameException, BadStepNameException {
		final ExternalProcessEntity process = getExternalProcessForConfigurationName(project, configurationName);
		return process.getConfiguration();
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
				for (final var job : stage.getProcesses().values()) {
					job.setStatus(null);
					job.setExternalProcessStatus(ProcessStatus.NOT_STARTED);
					job.setScheduledTime(null);
					job.setProcessUrl(null);
					job.getAdditionalResultFiles().clear();

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
	                                               final DataProcessingEntity dataProcessingEntity
	) throws BadDataConfigurationException, BadStateException {
		checkFile(project, dataConfiguration);

		final DataSetEntity dataSetEntity;

		if (dataProcessingEntity.getDataSet() == null) {
			dataSetEntity = new DataSetEntity(dataProcessingEntity);
		} else {
			dataSetEntity = dataProcessingEntity.getDataSet();
		}

		dataSetEntity.setDataConfiguration(dataConfiguration);

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

	private void storeDataSet(final DataSet dataSet,
	                          final DataSetEntity dataSetEntity) throws InternalDataSetPersistenceException {
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

	private DataSet exportDataSet(final DataSetEntity dataSetEntity, List<String> columnNames,
	                              @Nullable final List<Integer> rows, final boolean pagination,
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

		// If only specific rows should be selected but none are given, return empty dataset
		if (rows != null && rows.isEmpty()) {
			return new DataSet(dataRows, dataConfiguration);
		}

		try (final Statement exportStatement = connection.createStatement()) {

			final String exportQuery = rows != null
			                           ? createSelectQuery(dataSetEntity.getId(), columnNames, rows)
			                           : createSelectQuery(dataSetEntity.getId(), columnNames, pagination, startRow,
			                                               pageSize);

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

	private String createSelectQuery(final Long dataSetId, final List<String> columnNames,
	                                 final List<Integer> rowNumbers) {
		final List<String> quotedColumnNames = columnNames.stream().map(it -> "\"" + it + "\"").toList();
		return "SELECT " + String.join(",", quotedColumnNames) +
		       " FROM (SELECT *, ROW_NUMBER() OVER () AS row_num FROM " +
		       getTableName(dataSetId) + ") AS numbered_rows WHERE row_num IN (" +
		       rowNumbers.stream().map(rowNumber -> rowNumber + 1).map(Object::toString)
		                 .collect(Collectors.joining(",")) + ");";
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
		dataSetRepository.save(dataSet);
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

}
