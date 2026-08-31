package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.model.configuration.data.DataSourceConfiguration;
import de.kiaim.cinnamon.model.configuration.data.DatasetConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.model.data.*;
import de.kiaim.cinnamon.model.enumeration.DataSourceType;
import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.model.enumeration.ProcessStatus;
import de.kiaim.cinnamon.model.enumeration.StageStatus;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.configuration.Job;
import de.kiaim.cinnamon.platform.config.SerializationConfig;
import de.kiaim.cinnamon.platform.model.dto.*;
import de.kiaim.cinnamon.platform.model.entity.*;
import de.kiaim.cinnamon.model.configuration.data.file.FileType;
import de.kiaim.cinnamon.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.cinnamon.platform.model.mapper.DataSourceConfigurationMapper;
import de.kiaim.cinnamon.platform.model.mapper.DatasetConfigurationMapper;
import de.kiaim.cinnamon.platform.model.mapper.FileConfigurationMapper;
import de.kiaim.cinnamon.platform.processor.FhirProcessor;
import de.kiaim.cinnamon.platform.repository.DataProcessingRepository;
import de.kiaim.cinnamon.platform.repository.DataSetRepository;
import de.kiaim.cinnamon.platform.repository.DataTransformationErrorRepository;
import de.kiaim.cinnamon.platform.repository.ProjectRepository;
import de.kiaim.cinnamon.platform.helper.DataschemeGenerator;
import de.kiaim.cinnamon.platform.model.DataRowTransformationError;
import de.kiaim.cinnamon.platform.model.DataTransformationError;
import de.kiaim.cinnamon.platform.model.TransformationResult;
import de.kiaim.cinnamon.platform.model.enumeration.HoldOutSelector;
import de.kiaim.cinnamon.platform.model.enumeration.RowSelector;
import de.kiaim.cinnamon.model.configuration.data.file.FileConfiguration;
import de.kiaim.cinnamon.platform.processor.DataProcessor;
import lombok.extern.log4j.Log4j2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
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
@Log4j2
public class DatabaseService {

	private final static Set<ProcessStatus> targetStatus = Set.of(ProcessStatus.SKIPPED, ProcessStatus.FINISHED,
	                                                              ProcessStatus.ERROR, ProcessStatus.CANCELED);
	private final static Set<StageStatus> targetStageStatus = Set.of(StageStatus.FINISHED, StageStatus.ERROR,
	                                                                 StageStatus.CANCELED);

	private final Logger LOGGER = LoggerFactory.getLogger(DatabaseService.class);

	private final Connection connection;
	private final DataProcessingRepository dataProcessingRepository;
	private final DataSetRepository dataSetRepository;
	private final DataTransformationErrorRepository errorRepository;
	private final ProjectRepository projectRepository;

	private final DatasetConfigurationMapper datasetConfigurationMapper;
	private final DataSourceConfigurationMapper dataSourceConfigurationMapper;
	private final FileConfigurationMapper fileConfigurationMapper;

	private final DataschemeGenerator dataschemeGenerator;
	private final JsonMapper jsonMapper;

	private final DataSetService dataSetService;
	private final DataProcessorService dataProcessorService;
	private final DataSourceProcessorService dataSourceProcessorService;
	private final FhirProcessor fhirProcessor;
	private final StepService stepService;

	@Autowired
	public DatabaseService(final DataSource dataSource, final DataProcessingRepository dataProcessingRepository,
	                       final DataTransformationErrorRepository errorRepository,
	                       final SerializationConfig serializationConfig, final DataSetRepository dataSetRepository,
	                       final ProjectRepository projectRepository,
	                       final DatasetConfigurationMapper datasetConfigurationMapper,
	                       final DataSourceConfigurationMapper dataSourceConfigurationMapper,
	                       final FileConfigurationMapper fileConfigurationMapper,
	                       final DataschemeGenerator dataschemeGenerator,
	                       final DataSetService dataSetService,
	                       final DataProcessorService dataProcessorService,
	                       final DataSourceProcessorService dataSourceProcessorService,
	                       final FhirProcessor fhirProcessor,
	                       final StepService stepService) {
		this.connection = DataSourceUtils.getConnection(dataSource);
		this.dataProcessingRepository = dataProcessingRepository;
		this.errorRepository = errorRepository;
		jsonMapper = serializationConfig.jsonMapper();
		this.dataSetRepository = dataSetRepository;
		this.projectRepository = projectRepository;
		this.datasetConfigurationMapper = datasetConfigurationMapper;
		this.dataSourceConfigurationMapper = dataSourceConfigurationMapper;
		this.fileConfigurationMapper = fileConfigurationMapper;
		this.dataschemeGenerator = dataschemeGenerator;
		this.dataSetService = dataSetService;
		this.dataProcessorService = dataProcessorService;
		this.dataSourceProcessorService = dataSourceProcessorService;
		this.fhirProcessor = fhirProcessor;
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
	 * Stores the give data source configuration and associates it with the file of the original data of the given project.
	 *
	 * @param project                 The project of which the data source configuration should be stored.
	 * @param dataSourceConfiguration The data source configuration to be stored.
	 * @throws BadDataSetIdException               If the dataset is already stored.
	 * @throws InternalDataSetPersistenceException If the original data was stored and could not be deleted.
	 */
	@Transactional
	public void storeDataSourceConfiguration(
			final ProjectEntity project,
			final DataSourceConfiguration dataSourceConfiguration
	) throws BadDataSetIdException, InternalDataSetPersistenceException {
		deleteOriginalDataIfNotConfirmed(project);

		final DataSourceConfigurationEntity entity;
		if (project.getOriginalData().getFile().getDataSourceConfiguration() != null) {
			entity = project.getOriginalData().getFile().getDataSourceConfiguration();
		} else {
			entity = new DataSourceConfigurationEntity();
			project.getOriginalData().getFile().setDataSourceConfiguration(entity);
		}
		dataSourceConfigurationMapper.updateEntity(entity, dataSourceConfiguration);

		log.debug("Stored data source configuration");
	}

	@Transactional(readOnly = true)
	public DataSourceConfiguration exportDataSourceConfiguration(final ProjectEntity project) throws BadStateException {
		final DataSourceConfigurationEntity entity = project.getOriginalData().getFile().getDataSourceConfiguration();
		if (entity == null) {
			throw new BadStateException(BadStateException.NO_DATA_SOURCE_CONFIGURATION,
			                            "The project does not contain a data source configuration!");
		}
		return dataSourceConfigurationMapper.toDto(entity);
	}

	/**
	 * Stores the given file configuration and associates it with the file of the original data of the given project.
	 * Update the file entity if the file is already stored.
	 *
	 * @param project           The project of which the file configuration should be stored.
	 * @param fileConfiguration The file configuration to be stored.
	 * @throws BadDataSetIdException            If the dataset is already stored.
	 * @throws InternalIOException              If reading the dataset file failed.
	 * @throws InternalMissingHandlingException If no processor for the file type of the file could be found.
	 */
	@Transactional
	public void storeFileConfiguration(final ProjectEntity project, final FileConfiguration fileConfiguration)
			throws BadDataSetIdException, InternalDataSetPersistenceException, InternalIOException,
					       InternalMissingHandlingException {
		deleteDataSetIfNotConfirmedOrThrow(project.getOriginalData().getDataSet());

		final FileConfigurationEntity entity = fileConfigurationMapper.toEntity(fileConfiguration);
		project.getOriginalData().getFile().setFileConfiguration(entity);
		updateFileEntity(project);

		log.debug("Stored file configuration");
	}

	/**
	 * Exports the file configuration of the original data of the given project.
	 *
	 * @param project The project of which the file configuration should be exported.
	 * @return The file configuration of the original data of the given project.
	 */
	public FileConfiguration exportFileConfiguration(final ProjectEntity project) {
		final FileConfigurationEntity entity = project.getOriginalData().getFile().getFileConfiguration();
		return fileConfigurationMapper.toDto(entity);
	}

	/**
	 * Stores the given dataset configuration and associates it with the dataset of the original data of the given project.
	 * This is only allowed if no dataset is stored.
	 *
	 * @param project              The project of which the dataset configuration should be stored.
	 * @param datasetConfiguration The dataset configuration to be stored.
	 * @throws BadDataSetIdException If the dataset is already stored.
	 */
	@Transactional
	public void storeDatasetConfiguration(final ProjectEntity project, final DatasetConfiguration datasetConfiguration)
			throws BadDataSetIdException {
		throwIfStored(project.getOriginalData().getDataSet());
		doUpdateDatasetConfiguration(project, datasetConfiguration);
	}

	/**
	 * Updates the dataset configuration of the original data of the given project.
	 * If the original data is already stored, the hold-out split will be updated according to the new configuration.
	 *
	 * @param project              The project of which the dataset configuration should be updated.
	 * @param datasetConfiguration The dataset configuration to be updated.
	 * @throws BadArgumentException                If the given dataset configuration is invalid.
	 * @throws BadDataSetIdException               If the dataset is already confirmed.
	 * @throws BadStateException                   If the state of the data forbids to create the hold-out split.
	 * @throws InternalDataSetPersistenceException If updating the hold-out split failed.
	 */
	@Transactional
	public void updateDatasetConfiguration(final ProjectEntity project, final DatasetConfiguration datasetConfiguration)
			throws BadArgumentException, BadDataSetIdException, BadStateException, InternalDataSetPersistenceException {
		throwIfConfirmed(project.getOriginalData().getDataSet());
		doUpdateDatasetConfiguration(project, datasetConfiguration);
		updateHoldOutSplit(project);
	}

	/**
	 * Creates a DTO for the dataset configuration of the original data of the given project.
	 *
	 * @param project The project of which the dataset configuration should be returned.
	 * @return The dataset configuration of the original data of the given project.
	 */
	@Transactional
	public DatasetConfiguration getDatasetConfiguration(final ProjectEntity project) {
		final DatasetConfigurationEntity datasetConfigurationEntity = project.getOriginalData().getDatasetConfiguration();
		return datasetConfigurationMapper.toDto(datasetConfigurationEntity);
	}

	/**
	 * Updates the file.
	 * If the file is or the file configuration is not stored, nothing will be done.
	 * It does not check if the dataset is confirmed, this has to be done by the caller.
	 *
	 * @param project The project to update.
	 * @throws InternalIOException              If reading the dataset file failed.
	 * @throws InternalMissingHandlingException If no processor for the file type of the file could be found.
	 */
	@Transactional
	protected void updateFileEntity(final ProjectEntity project)
			throws InternalIOException, InternalMissingHandlingException {
		// Check if the file and the file configuration are available
		final FileEntity fileEntity = project.getOriginalData().getFile();
		final LobWrapperEntity file = fileEntity.getFile();
		if (file == null) {
			return;
		}
		final FileConfigurationEntity fileConfiguration = fileEntity.getFileConfiguration();
		if (fileConfiguration == null) {
			return;
		}

		// Update the file-related properties
		final DataProcessor dataProcessor = dataProcessorService.getDataProcessor(fileConfiguration.getFileType());
		final int numberOfAttributes = dataProcessor.getNumberColumns(file.getLobStream(), fileConfiguration);
		fileEntity.setNumberOfAttributes(numberOfAttributes);
	}

	/**
	 * Retrieves the file for the original data and stores it in the database.
	 *
	 * @param project The project to retrieve the file for.
	 * @return General information about the retrieved file.
	 * @throws BadDataSetIdException            If the dataset is already stored.
	 * @throws BadFileException                 If the file could not be read.
	 * @throws BadStateException                If no file configuration is available for the project.
	 * @throws InternalIOException              If reading the data failed.
	 * @throws InternalMissingHandlingException If no processor exists for the selected data source type.
	 * @throws InternalRequestException         If the request retrieving the file from the server failed.
	 */
	@Transactional
	public FileInformation retrieveAndStoreFile(final ProjectEntity project)
			throws BadDataSetIdException, BadFileException, BadStateException, InternalDataSetPersistenceException,
					       InternalIOException, InternalMissingHandlingException, InternalRequestException {
		final Pair<FileType, MultipartFile> result = retrieveFile(project);
		if (result == null) {
			throw new BadStateException(BadStateException.NO_EXTERNAL_DATA_SOURCE,
			                            "Failed to retrieve the file! The data source is set to be a local file.");
		}
		storeFile(project, result.getSecond());

		return getFileInformation(project);
	}

	/**
	 * Estimates the file configuration for the data file of the given project.
	 * Stores the estimation in the database.
	 *
	 * @param project  The project to estimate the file configuration for.
	 * @return The estimation result.
	 * @throws BadDataSetIdException            If the project does not have a data set.
	 * @throws BadFileException                 If the file could not be processed.
	 * @throws BadStateException                If no file is available for the project.
	 * @throws InternalIOException              If an internal I/O error occurred.
	 * @throws InternalMissingHandlingException If no processor for the file type of the file could be found.
	 */
	@Transactional
	public FileConfigurationEstimation estimateAndStoreFileConfiguration(
			final ProjectEntity project
	) throws BadDataSetIdException, BadFileException, BadStateException, InternalDataSetPersistenceException,
			         InternalIOException, InternalMissingHandlingException {
		final FileEntity file = project.getOriginalData().getFile();

		if (file.getFile() == null) {
			throw new BadStateException(BadStateException.NO_DATASET_FILE,
			                            "Failed to estimate the file configuration! No file is available!");
		}

		final FileType fileType = dataProcessorService.getFileTypeByFileName(file.getName());
		final DataProcessor dataProcessor = dataProcessorService.getDataProcessor(fileType);
		final FileConfigurationEstimation estimation = dataProcessor.estimateFileConfiguration(file.getFile(),
		                                                                                       file.getCompatibility());

		storeFileConfiguration(project, estimation.getEstimation());
		return estimation;
	}

	/**
	 * Retrieves the file for the original data of the given project.
	 * If the configuration is not available or the local file should be used, the local given file is returned.
	 *
	 * @param project The project to retrieve the file for.
	 * @return The file type and the file.
	 * @throws BadStateException If no file configuration is available for the project.
	 * @throws InternalMissingHandlingException If no processor exists for the selected data source type.
	 * @throws InternalRequestException If the request retrieving the file from the server failed.
	 */
	@Nullable
	public Pair<FileType, MultipartFile> retrieveFile(final ProjectEntity project)
			throws BadStateException, InternalMissingHandlingException, InternalRequestException {
		var config = project.getOriginalData().getFile().getDataSourceConfiguration();

		if (config == null) {
			throw new BadStateException(BadStateException.NO_DATA_SOURCE_CONFIGURATION,
			                            "Retrieving the file requires the data source configuration to be available!");
		}

		if (config.getDataSourceType() == DataSourceType.LOCAL || config.getServer() == null) {
			return null;
		}

		final var processor = dataSourceProcessorService.getProcessor(config.getDataSourceType());
		return processor.retrieveFile(config.getServer());
	}

	/**
	 * Stores the given file.
	 * If a dataset is already stored for the original data, an exception will be thrown.
	 *
	 * @param project The project where the file should be stored.
	 * @param file    The file to be stored.
	 * @return General information about the file.
	 * @throws BadDataSetIdException            If the data set has already been confirmed.
	 * @throws BadFileException                 If the file could not be read.
	 * @throws InternalIOException              If reading the data failed.
	 * @throws InternalMissingHandlingException If no processor for the file type of the file could be found.
	 */
	@Transactional
	public FileInformation storeFile(final ProjectEntity project, final MultipartFile file)
			throws BadDataSetIdException, BadFileException, InternalDataSetPersistenceException, InternalIOException,
					       InternalMissingHandlingException {
		deleteDataSetIfNotConfirmedOrThrow(project.getOriginalData().getDataSet());

		dataProcessorService.validateFileOrThrow(file);

		final FileEntity fileEntity = project.getOriginalData().getFile();
		fileEntity.setName(file.getOriginalFilename());

		try {
			fileEntity.setFile(new LobWrapperEntity(file.getBytes()));
		} catch (final IOException e) {
			throw new BadFileException(BadFileException.NOT_READABLE, "Could not read file");
		}

		determineCompatibleFileTypes(project, fileEntity.getFile());
		updateFileEntity(project);

		log.debug("Stored file containing original data '{}'", file.getOriginalFilename());

		return getFileInformation(project);
	}

	/**
	 * Determines which processors can read the given file.
	 *
	 * @param project The project of which the file information should be returned.
	 * @param file The file to be checked. Must be part of the given project.
	 */
	@Transactional
	protected void determineCompatibleFileTypes(final ProjectEntity project, final LobWrapperEntity file) {
		final FileEntity fileEntity = project.getOriginalData().getFile();

		final FileCompatibilityEntity compatibility = new FileCompatibilityEntity();

		for (final DataProcessor processor : dataProcessorService.getProcessors()) {
			processor.checkFileCompatibility(file, compatibility);
		}

		fileEntity.setCompatibility(compatibility);
	}

	/**
	 * Creates a DTO for the file information of the original data of the given project.
	 * If no file is available, the fields will be null or 0.
	 *
	 * @param project The project of which the file information should be returned.
	 * @return The file information of the original data of the given project.
	 */
	@Transactional
	public FileInformation getFileInformation(final ProjectEntity project) {
		final var fileInformation = new FileInformation();

		final FileEntity file = project.getOriginalData().getFile();
		fileInformation.setName(file.getName());
		fileInformation.setNumberOfAttributes(file.getNumberOfAttributes());

		if (file.getCompatibility() != null) {
			fileInformation.setFhirResourceTypes(file.getCompatibility().getFhirResourceTypes());
		}

		if (file.getDataSourceConfiguration() != null) {
			fileInformation.setDataSourceType(file.getDataSourceConfiguration().getDataSourceType());
		}

		if (file.getFileConfiguration() != null) {
			fileInformation.setType(file.getFileConfiguration().getFileType());
		} else if (file.getName() != null) {
			try {
				dataProcessorService.getFileTypeByFileName(file.getName());
			} catch (final BadFileException ignored) {
			}
		}

		return fileInformation;
	}

	/**
	 * Stores the DataConfiguration and associates the configuration with the data set for the given step in the given configuration.
	 *
	 * @param dataConfiguration The configuration to be stored.
	 * @param project           The project of the data set the configuration should be associated with.
	 * @throws BadDataSetIdException If the data has already been confirmed.
	 */
	@Transactional
	public void storeOriginalDataConfiguration(final DataConfiguration dataConfiguration, final ProjectEntity project)
			throws BadDataSetIdException {
		throwIfStored(project.getOriginalData().getDataSet());
		doStoreOriginalDataConfiguration(project, dataConfiguration);
	}

	/**
	 * Estimates the data configuration for the original data based on the file and the file configuration
	 * currently stored in the given project.
	 *
	 * @param project The project to estimate the data configuration for.
	 * @return The estimation result.
	 * @throws BadDataSetIdException            If the dataset is already stored.
	 * @throws BadStateException                If the file or the file configuration are not available.
	 * @throws InternalIOException              If reading the file failed.
	 * @throws InternalMissingHandlingException If no processor for the file type of the file could be found.
	 */
	@Transactional
	public DataConfigurationEstimation estimateOriginalDataConfiguration(final ProjectEntity project)
			throws BadDataSetIdException, BadStateException, InternalIOException, InternalMissingHandlingException {
		// Check if the file and the file configuration are available
		final FileEntity fileEntity = project.getOriginalData().getFile();
		final LobWrapperEntity file = fileEntity.getFile();
		if (file == null) {
			throw new BadStateException(BadStateException.NO_DATASET_FILE,
			                            "Estimating the data configuration requires the file for the dataset to be selected!");
		}
		final FileConfigurationEntity fileConfiguration = fileEntity.getFileConfiguration();
		if (fileConfiguration == null) {
			throw new BadStateException(BadStateException.NO_DATASET_FILE_CONFIGURATION,
			                            "Estimating the data configuration requires the file configuration!");
		}

		final DataProcessor dataProcessor = dataProcessorService.getDataProcessor(fileConfiguration.getFileType());
		DataConfigurationEstimation estimation = dataProcessor.estimateDataConfiguration(file.getLobStream(),
		                                                                                 fileConfiguration,
		                                                                                 DatatypeEstimationAlgorithm.MOST_ESTIMATED);
		storeOriginalDataConfiguration(estimation.getDataConfiguration(), project);
		return estimation;
	}

	/**
	 * Update the dataset of the original data with the currently stored configurations and file.
	 * This is only allowed if the dataset is not confirmed yet.
	 *
	 * @param project The project to update.
	 * @throws BadArgumentException                If the given configurations are invalid.
	 * @throws BadDataConfigurationException       If the number of attributes does not match with the stored data configuration.
	 * @throws BadDatasetException                 If the data file could not be converted into a table.
	 * @throws BadDataSetIdException               If the dataset is already confirmed.
	 * @throws BadStateException                   If the file or any configuration are not available.
	 * @throws InternalDataSetPersistenceException If the dataset could not be stored due to an internal error.
	 * @throws InternalIOException                 If reading the file failed.
	 * @throws InternalMissingHandlingException    If no processor for the file type of the file could be found.
	 */
	@Transactional
	public Long storeOriginalDataset(final ProjectEntity project)
			throws BadArgumentException, BadDataConfigurationException, BadDatasetException, BadDataSetIdException,
					       BadStateException, InternalDataSetPersistenceException, InternalIOException,
					       InternalMissingHandlingException {
		// Check if the file and the file configuration are available
		final FileEntity fileEntity = project.getOriginalData().getFile();
		final LobWrapperEntity file = fileEntity.getFile();
		if (file == null) {
			throw new BadStateException(BadStateException.NO_DATASET_FILE,
			                            "Storing the dataset requires the file for the dataset to be selected!");
		}
		final FileConfigurationEntity fileConfiguration = fileEntity.getFileConfiguration();
		if (fileConfiguration == null) {
			throw new BadStateException(BadStateException.NO_DATASET_FILE_CONFIGURATION,
			                            "Storing the dataset requires the file configuration!");
		}
		// Check if the dataset is available
		final DataSetEntity originalDataSet = project.getOriginalData().getDataSet();
		if (originalDataSet == null) {
			throw new BadStateException(BadStateException.NO_ORIGINAL_DATA_CONFIGURATION,
			                            "Storing the dataset requires the attributes to be configured!");
		}
		// Check if the data configuration is available
		final DataConfiguration configuration = originalDataSet.getDataConfiguration();
		if (configuration == null) {
			throw new BadStateException(BadStateException.NO_ORIGINAL_DATA_CONFIGURATION,
			                            "Storing the dataset requires the attributes to be configured!");
		}

		// Store the dataset
		final DataProcessor dataProcessor = dataProcessorService.getDataProcessor(fileConfiguration.getFileType());
		final TransformationResult transformationResult = dataProcessor.read(file.getLobStream(), fileConfiguration,
		                                                                     configuration);
		final Long id = storeOriginalTransformationResult(transformationResult, project);
		updateHoldOutSplit(project);

		return id;
	}

	/**
	 * Deletes the original dataset table associated with the given project.
	 * If the dataset is confirmed, an exception will be thrown.
	 * If no dataset is stored, nothing will be done.
	 *
	 * @param projectEntity   The project of which the dataset should be deleted.
	 * @throws BadDataSetIdException               If the dataset is confirmed.
	 * @throws InternalDataSetPersistenceException If there is an issue with the dataset persistence.
	 */
	@Transactional
	public void deleteOriginalDataset(final ProjectEntity projectEntity)
			throws BadDataSetIdException, InternalDataSetPersistenceException {
		deleteDataSetIfNotConfirmedOrThrow(projectEntity.getOriginalData().getDataSet());
	}

	/**
	 * Similar to {@link #deleteOriginalDataset(ProjectEntity)}, but does not throw an exception if the dataset is confirmed.
	 */
	@Transactional
	public void deleteOriginalDatasetIgnoreConfirmed(final ProjectEntity projectEntity)
			throws InternalDataSetPersistenceException {
		deleteDataSet(projectEntity.getOriginalData().getDataSet());
	}

	/**
	 * Stores the given TransformationResult as the original data by storing the DataSet,
	 * the DataConfiguration, and the transformation errors into the database
	 * and associates them with the given step in the given project.
	 * The table for the DataSet will be generated automatically.
	 * Returns an ID to access the data.
	 *
	 * @param transformationResult The transformation result to be stored.
	 * @param project              The project.
	 * @return The ID of the data set.
	 * @throws BadDataConfigurationException       If the number of attributes does not match with the stored data configuration.
	 * @throws BadDataSetIdException               If the data set is already stored.
	 * @throws BadStateException                   If no file for the original data has been selected.
	 * @throws InternalDataSetPersistenceException If the data set could not be stored.
	 * @throws InternalIOException                 If reading the FHIR bundle file from the database failed.
	 */
	@Transactional
	public Long storeOriginalTransformationResult(final TransformationResult transformationResult,
	                                              final ProjectEntity project)
			throws BadDataConfigurationException, BadDataSetIdException, BadStateException, InternalDataSetPersistenceException, InternalIOException {
		final DataSet dataSet = transformationResult.getDataSet();
		final DataConfiguration dataConfiguration = dataSet.getDataConfiguration();

		// Test configuration
		checkFile(project, dataConfiguration);

		// Delete the existing data set
		deleteDataSetIfNotConfirmedOrThrow(project.getOriginalData().getDataSet());

		// Store configuration
		DataSetEntity dataSetEntity = doStoreOriginalDataConfiguration(project, dataSet.getDataConfiguration());

		// Store transformation errors
		convertTransformationErrors(transformationResult, dataSetEntity);

		dataSetEntity = storeDataSet(dataSet, dataSetEntity);

		log.debug("Stored transformation result for original data");

		return dataSetEntity.getId();
	}

	/**
	 * Stores the given TransformationResult by storing the DataSet,
	 * the DataConfiguration, and the transformation errors into the database
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
	 * @throws InternalIOException                 If reading the FHIR bundle file from the database failed.
	 */
	@Transactional
	public void storeTransformationResult(final TransformationResult transformationResult,
	                                      final DataProcessingEntity dataProcessingEntity,
	                                      final List<Job> processed)
			throws BadDataConfigurationException, BadDataSetIdException, BadStateException, InternalDataSetPersistenceException, InternalIOException {
		final ProjectEntity project = dataProcessingEntity.getExecutionStep().getPipeline().getProject();
		final DataSet dataSet = transformationResult.getDataSet();
		final DataConfiguration dataConfiguration = dataSet.getDataConfiguration();

		// Test configuration
		checkFile(project, dataConfiguration);

		// Delete the existing data set
		deleteDataSetIfNotConfirmedOrThrow(dataProcessingEntity.getDataSet());

		// Store configuration
		final DataSetEntity dataSetEntity = doStoreDataConfiguration(dataSet.getDataConfiguration(),
		                                                             dataProcessingEntity, processed);

		// Store transformation errors
		convertTransformationErrors(transformationResult, dataSetEntity);

		dataProcessingRepository.save(dataProcessingEntity);

		storeDataSet(dataSet, dataSetEntity);

		log.debug("Stored transformation result for job {}", dataProcessingEntity.getJob().getName());
	}

	/**
	 * Updates the hold-out split of the original data set according to the current dataset configuration.
	 *
	 * @param project The project to update.
	 * @throws BadStateException                   If the state of the data forbids to create the hold-out split.
	 * @throws BadArgumentException                If the given percentage is invalid.
	 * @throws InternalDataSetPersistenceException If executing the queries failed.
	 */
	@Transactional
	protected void updateHoldOutSplit(final ProjectEntity project)
			throws BadArgumentException, BadStateException, InternalDataSetPersistenceException {
		// Check if the dataset is available and if the data was stored before
		final DataSetEntity originalDataSet = project.getOriginalData().getDataSet();
		if (originalDataSet == null || !originalDataSet.isStoredData()) {
			return;
		}

		final DatasetConfigurationEntity datasetConfiguration = project.getOriginalData().getDatasetConfiguration();
		if (!datasetConfiguration.isCreateHoldOutSplit() || datasetConfiguration.getHoldOutSplitPercentage() == 0) {
			removeHoldOutSplit(originalDataSet);
		} else {
			createHoldOutSplit(project, datasetConfiguration.getHoldOutSplitPercentage());
		}
	}

	/**
	 * Removes the hold-out split from the given data set.
	 *
	 * @param dataSet The data set to remove the hold-out split from.
	 * @throws InternalDataSetPersistenceException If executing the queries failed.
	 */
	@Transactional
	protected void removeHoldOutSplit(final DataSetEntity dataSet) throws InternalDataSetPersistenceException {
		if (!dataSet.isHasHoldOut()) {
			return;
		}

		final String tableName = getTableName(dataSet.getId());

		try {
			setAllHoldOutRows(tableName, false);
		} catch (final SQLException e) {
			throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.HOLD_OUT,
			                                              "Failed to reset the hold-out split!", e);
		}

		dataSet.setHasHoldOut(false);
		dataSet.setHoldOutSeed(0);

		log.debug("Removed hold-out split for dataset {}", dataSet.getId());
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
	protected void createHoldOutSplit(final ProjectEntity project, final float holdOutPercentage)
			throws BadStateException, BadArgumentException, InternalDataSetPersistenceException {
		final DataSetEntity dataset = project.getOriginalData().getDataSet();
		if (dataset == null || !dataset.isStoredData()) {
			throw new BadStateException(BadStateException.NO_DATA_SET,
			                            "Creating the hold-out split requires the original date set to be stored!");
		}

		if (dataset.isConfirmedData()) {
			throw new BadStateException(BadStateException.DATE_CONFIRMED,
			                            "Creating the hold-out split cannot be done after the data has been confirmed!");
		}

		if (holdOutPercentage < 0 || holdOutPercentage > 1) {
			throw new BadArgumentException(BadArgumentException.HOLD_OUT_PERCENTAGE,
			                               "Hold out percentage must be between 0 and 1!");
		}

		// Reset existing hold-out split
		removeHoldOutSplit(project.getOriginalData().getDataSet());
		projectRepository.save(project);

		// Set the seed
		final int seed = project.randomInt();
		dataset.setHoldOutSeed(seed);

		// Create new hold-out split
		try {
			createHoldOutSplit(dataset, holdOutPercentage, seed);
		} catch (final SQLException e) {
			throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.HOLD_OUT,
			                                              "Failed to create the hold-out split!", e);
		}

		dataset.setHasHoldOut(true);
		projectRepository.save(project);

		log.debug("Created hold-out split with percentage {} for dataset {}", holdOutPercentage, dataset.getId());
	}

	/**
	 * Creates the hold-out split for the given dataset.
	 *
	 * @param dataset           The dataset for which the hold-out split should be created.
	 * @param holdOutPercentage The percentage of rows that should be added to the hold-out split. Must be between 0 and 1.
	 * @param seed              The seed for the random number generator used to create the hold-out split.
	 * @throws InternalDataSetPersistenceException If the number of rows could not be retrieved.
	 * @throws SQLException                        If an error occurs while interacting with the database.
	 */
	private void createHoldOutSplit(final DataSetEntity dataset, final float holdOutPercentage, final int seed)
			throws InternalDataSetPersistenceException, SQLException {
		final String tableName = getTableName(dataset.getId());

		final int rowCount = countEntries(dataset.getId());
		final int holdOutRows = Math.round(rowCount * holdOutPercentage);

		if (holdOutRows <= 0) {
			return;
		}

		if (holdOutRows >= rowCount) {
			setAllHoldOutRows(tableName, true);
			return;
		}

		/*
		 * For large percentages, it is cheaper to mark all rows as hold-out
		 * and then mark only the smaller non-hold-out sample back to false.
		 */
		if (holdOutRows <= rowCount / 2) {
			final Set<Integer> selectedRows = sampleRowNumbers(rowCount, holdOutRows, seed);
			updateHoldOutRowsChunked(tableName, selectedRows, true);
		} else {
			setAllHoldOutRows(tableName, true);

			final int nonHoldOutRows = rowCount - holdOutRows;
			final Set<Integer> selectedRows = sampleRowNumbers(rowCount, nonHoldOutRows, seed);
			updateHoldOutRowsChunked(tableName, selectedRows, false);
		}
	}

	/**
	 * Samples the given number of row numbers from the given row count.
	 *
	 * @param rowCount   The total number of rows.
	 * @param sampleSize The number of rows that should be sampled.
	 * @param seed       The seed for the random number generator.
	 * @return Set of row numbers that were sampled.
	 */
	private Set<Integer> sampleRowNumbers(final int rowCount, final int sampleSize, final int seed) {
		final Random random = new Random(seed);
		final Set<Integer> selectedRows = new HashSet<>(sampleSize);

		for (int i = rowCount - sampleSize; i < rowCount; i++) {
			final int candidate = random.nextInt(i + 1);

			if (!selectedRows.add(candidate)) {
				selectedRows.add(i);
			}
		}

		return selectedRows;
	}

	/**
	 * Sets the hold-out flag for all rows in the given table to the given value.
	 *
	 * @param tableName The name of the table.
	 * @param holdOut   Flag value.
	 * @throws SQLException If setting the hold-out flag failed.
	 */
	private void setAllHoldOutRows(final String tableName, final boolean holdOut) throws SQLException {
		final String query =
				"UPDATE " + tableName +
				" SET " + DataschemeGenerator.HOLD_OUT_FLAG_NAME + " = ?";

		try (final PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setQueryTimeout(20);
			statement.setBoolean(1, holdOut);
			statement.executeUpdate();
		}
	}

	/**
	 * Sets the hold-out flag for the given row numbers in the given table to the given value.
	 * Uses chunking to avoid issues with too many parameters in the query for large data sets.
	 *
	 * @param tableName  The name of the table.
	 * @param rowNumbers The row numbers to update.
	 * @param holdOut    Flag value.
	 * @throws SQLException If updating the hold-out flag failed.
	 */
	private void updateHoldOutRowsChunked(final String tableName, final Collection<Integer> rowNumbers,
	                                      final boolean holdOut)
			throws SQLException {
		final int chunkSize = 500;
		final List<Integer> rowNumberList = new ArrayList<>(rowNumbers);

		for (int start = 0; start < rowNumberList.size(); start += chunkSize) {
			final int end = Math.min(start + chunkSize, rowNumberList.size());
			updateHoldOutRows(tableName, rowNumberList.subList(start, end), holdOut);
		}
	}

	/**
	 * Updates the hold-out flag for the given row numbers in the given table.
	 * Prefer using {@link #updateHoldOutRowsChunked(String, Collection, boolean)} for better performance with large datasets.
	 *
	 * @param tableName  The name of the table.
	 * @param rowNumbers The row numbers to update.
	 * @param holdOut    Flag value.
	 * @throws SQLException If updating the hold-out flag failed.
	 */
	private void updateHoldOutRows(final String tableName, final List<Integer> rowNumbers, final boolean holdOut)
			throws SQLException {
		if (rowNumbers.isEmpty()) {
			return;
		}

		final String placeholders = String.join(",", Collections.nCopies(rowNumbers.size(), "?"));
		final String query =
				"UPDATE " + tableName +
				" SET " + DataschemeGenerator.HOLD_OUT_FLAG_NAME + " = ?" +
				" WHERE " + DataschemeGenerator.ROW_INDEX_NAME + " IN (" + placeholders + ")";

		try (final PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setQueryTimeout(20);
			statement.setBoolean(1, holdOut);

			for (int i = 0; i < rowNumbers.size(); i++) {
				statement.setInt(i + 2, rowNumbers.get(i));
			}

			statement.executeUpdate();
		}
	}

	/**
	 * Stores an arbitrary configuration under the given identifier.
	 * If a configuration with the given name is already present, it will be overwritten.
	 * If the configuration has changed and is used by processes, marks them as outdated.
	 *
	 * @param configName    Identifier for the configuration.
	 * @param configuration Configuration to store.
	 * @param project       The project the configuration should be associated with.
	 * @throws BadConfigurationNameException If the configuration name is not defined.
	 * @throws BadStateException             If the process is running or scheduled.
	 */
	@Transactional
	public void storeConfiguration(final String configName, @Nullable final String configuration,
	                               final ProjectEntity project
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

			for (final var usage : config.getUsages()) {

				if (usage.getExternalProcessStatus() == ProcessStatus.SCHEDULED ||
				    usage.getExternalProcessStatus() == ProcessStatus.RUNNING) {
					throw new BadStateException(BadStateException.PROCESS_STARTED,
					                            "Process cannot be configured if the it is scheduled or started!");
				}
			}
		}

		if (!Objects.equals(configuration, config.getConfiguration())) {
			config.setConfiguration(configuration);

			for (final BackgroundProcessEntity usage: config.getUsages()) {
				markProcessOutdated(usage);
			}
		}

		log.debug("Stored configuration for {}", configName);

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
	 *                                                   If the given dataset is not the original one and the project does not have one.
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
		return getInfo(dataSetEntity);
	}

	/**
	 * Returns the info objects of the given dataset.
	 *
	 * @param dataSetEntity The dataset.
	 * @return The info object.
	 * @throws BadStateException                   If the given dataset is not the original one and the project does not have one.
	 * @throws InternalDataSetPersistenceException If the internal queries failed.
	 */
	public DataSetInfo getInfo(
			DataSetEntity dataSetEntity) throws BadStateException, InternalDataSetPersistenceException {
		final OriginalDataEntity originalData = dataSetEntity.getOriginalData();
		final DataConfigurationInfo dataConfigurationInfo = getDataConfigurationInfo(
				dataSetEntity.getDataConfiguration());

		if (!dataSetEntity.isStoredData()) {
			final Integer numberRetainedRows = originalData == null ? 0 : null;
			return new DataSetInfo(0, 0, false, 0.0f, 0, 0, numberRetainedRows, dataConfigurationInfo);
		}

		final int rows = getNumberRows(dataSetEntity);
		final int invalidRows = countInvalidRows(dataSetEntity.getId());

		boolean hasHoldOutSplit = dataSetEntity.isHasHoldOut();
		float holdOutPercentage = 0.0f;

		int numberHoldOutRows = 0;
		int numberInvalidHoldOutRows = 0;

		Integer numberRetainedRows = null;

		if (originalData != null) {
			holdOutPercentage = originalData.getDatasetConfiguration().getHoldOutSplitPercentage();

			if (hasHoldOutSplit) {
				numberHoldOutRows = getNumberHoldOutRows(dataSetEntity);
				numberInvalidHoldOutRows = countEntries(dataSetEntity.getId(), HoldOutSelector.HOLD_OUT, RowSelector.ERRORS, null);
			}
		} else {
			numberRetainedRows = getNumberOfRetainedRows(dataSetEntity);
		}

		return new DataSetInfo(rows, invalidRows, hasHoldOutSplit, holdOutPercentage, numberHoldOutRows,
		                       numberInvalidHoldOutRows, numberRetainedRows, dataConfigurationInfo);
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
	 * Exports the configuration of the original dataset of the given project.
	 *
	 * @param project The project of which the configuration should be exported.
	 * @return The data configuration.
	 * @throws BadStateException   If the data configuration does not exist.
	 * @throws InternalIOException If the DataConfiguration could not be deserialized from the stored JSON.
	 */
	@Transactional
	public DataConfiguration exportOriginalDataConfiguration(final ProjectEntity project)
			throws BadStateException, InternalIOException {
		if (project.getOriginalData().getDataSet() == null) {
			throw new BadStateException(BadStateException.NO_ORIGINAL_DATA_CONFIGURATION,
			                            "No original data configuration available!");
		}

		final DataSetEntity dataSetEntity = project.getOriginalData().getDataSet();
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
	public DataSet exportDataSet(final ProjectEntity project, final HoldOutSelector holdOutSelector,
	                             final DataSetSource dataSetSource)
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
	 * After confirming, the data cannot be overwritten, only be deleted.
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

		log.debug("Confirmed original dataset");

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
	public TransformationResult exportTransformationResult(final ProjectEntity project,
	                                                       final HoldOutSelector holdOutSelector,
	                                                       final DataSetSource dataSetSource)
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

		var hasHoldOut = dataSetEntity.isHasHoldOut();
		var calcRowNumbers = rowSelector != RowSelector.ALL ||
		                     (hasHoldOut && loadDataRequest.getHoldOutSelector() != HoldOutSelector.ALL);

		final var startRow = (pageNumber - 1) * pageSize;

		final Map<Integer, Integer> columnIndexMapping = dataSetService.getColumnIndexMapping(
				dataSetEntity.getDataConfiguration(), columnNames);
		final DataSet dataSet = exportDataSet(dataSetEntity, rowSelector, columnNames,
		                                      loadDataRequest.getHoldOutSelector(), true, startRow, pageSize,
		                                      calcRowNumbers);

		final List<Integer> rowNumbers;

		final Set<DataTransformationErrorEntity> errors;
		if (calcRowNumbers) {
			rowNumbers = dataSet.getData().stream().map(a -> (Integer) a.get(a.size() - 1)).toList();
			errors = errorRepository.findByDataSetIdAndRowIndexIn(dataSetEntity.getId(), rowNumbers);
		} else {
			var endRow = startRow + dataSet.getDataRows().size();
			errors = errorRepository.findByDataSetIdAndRowIndexBetween(dataSetEntity.getId(), startRow, endRow - 1);
			rowNumbers = IntStream.range(startRow, endRow)
			                      .boxed()
			                      .collect(java.util.stream.Collectors.toList());
		}

		List<List<Object>> data = dataSetService.encodeDataRows(dataSet, errors, startRow, rowNumbers,
		                                                        columnIndexMapping,
		                                                        loadDataRequest);

		if (calcRowNumbers) {
			data = data.stream().map(a -> a.subList(0, a.size() - 1)).toList();
		}

		final int numberRows = countEntries(dataSetEntity.getId(), loadDataRequest.getHoldOutSelector(), rowSelector,
		                                    columnIndexMapping.keySet());
		final int numberPages = (int) Math.ceil((float) numberRows / pageSize);

		final Map<Integer, DataRowTransformationError> rowErrors = new HashMap<>();
		for (final var error : errors) {
			if (!columnIndexMapping.containsKey(error.getColumnIndex())) {
				continue;
			}

			if (!rowErrors.containsKey(error.getRowIndex())) {
				final int index =
						rowNumbers != null ? rowNumbers.indexOf(error.getRowIndex()) : error.getRowIndex() - startRow;
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
	 * Removes the file, dataset and transformation errors of the original data associated with the given project
	 * from the database and deletes the corresponding table.
	 *
	 * @param project The project of which the original data set should be deleted.
	 * @throws InternalDataSetPersistenceException If the data set could not be deleted due to an internal error.
	 */
	@Transactional
	public void deleteOriginalData(final ProjectEntity project) throws InternalDataSetPersistenceException {
		project.getOriginalData().getFile().setName(null);
		project.getOriginalData().getFile().setNumberOfAttributes(0);
		project.getOriginalData().getFile().setFileConfiguration(null);
		project.getOriginalData().getFile().setCompatibility(null);
		project.getOriginalData().getFile().setFile(null);

		project.getOriginalData().getDatasetConfiguration().setCreateHoldOutSplit(false);
		project.getOriginalData().getDatasetConfiguration().setHoldOutSplitPercentage(0.0f);

		final DataSetEntity dataSet = project.getOriginalData().getDataSet();
		if (dataSet != null) {
			project.getOriginalData().setDataSet(null);
			deleteDataSet(dataSet);
			dataSetRepository.delete(dataSet);
		}
	}

	@Transactional
	public void deleteOriginalDataIfNotConfirmed(final ProjectEntity project)
			throws BadDataSetIdException, InternalDataSetPersistenceException {
		throwIfConfirmed(project.getOriginalData().getDataSet());
		deleteOriginalData(project);
	}

	/**
	 * Deletes the data, transformation errors and statistics for the given dataset.
	 *
	 * @param dataSet The dataset to delete.
	 * @throws InternalDataSetPersistenceException If the table could not be deleted.
	 */
	@Transactional
	public void deleteDataSet(@Nullable final DataSetEntity dataSet) throws InternalDataSetPersistenceException {
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
		dataSet.setHasHoldOut(false);
		dataSet.setHoldOutSeed(0);
		dataSet.setConfirmedData(false);
		dataSet.getStatisticsProcess().reset();

		log.debug("Deleted dataset with ID {}", dataSet.getId());
	}

	/**
	 * Returns the number of rows in the given dataset.
	 *
	 * @param dataSetEntity The dataset.
	 * @return The number of rows in the dataset.
	 * @throws InternalDataSetPersistenceException If the number could not be retrieved.
	 */
	public int getNumberRows(final DataSetEntity dataSetEntity) throws InternalDataSetPersistenceException {
		return countEntries(dataSetEntity.getId());
	}

	/**
	 * Returns the number of hold-out rows in the given dataset.
	 *
	 * @param dataSetEntity The dataset.
	 * @return The number of hold-out rows in the dataset.
	 * @throws InternalDataSetPersistenceException If the number could not be retrieved.
	 */
	public int getNumberHoldOutRows(final DataSetEntity dataSetEntity) throws InternalDataSetPersistenceException {
		return dataSetEntity.isHasHoldOut()
		       ? countEntries(dataSetEntity.getId(), HoldOutSelector.HOLD_OUT, RowSelector.ALL, null)
		       : 0;
	}

	/**
	 * Counts the number of rows in the dataset with the given ID.
	 *
	 * @param dataSetId The ID of the dataset.
	 * @return The number of rows in the dataset.
	 * @throws InternalDataSetPersistenceException If the Number could not be retrieved.
	 */
	public int countEntries(final long dataSetId) throws InternalDataSetPersistenceException {
		return countEntries(dataSetId, HoldOutSelector.ALL, RowSelector.ALL, null);
	}

	/**
	 * Counts the number of entries in the dataset that comply with the given selectors.
	 *
	 * @param dataSetId       The ID of the data set.
	 * @param holdOutSelector If hold-out rows should be selected.
	 * @param rowSelector     Selector specifying which rows should be included regarding on the hold-out split.
	 * @param columnIndices   Columns the row selector condition should be applied to.
	 *                        If null, the condition is applied to all columns.
	 * @return The number of entries.
	 * @throws InternalDataSetPersistenceException If the number could not be retrieved.
	 */
	public int countEntries(final long dataSetId, final HoldOutSelector holdOutSelector, final RowSelector rowSelector,
	                        @Nullable final Collection<Integer> columnIndices) throws InternalDataSetPersistenceException {
		String countQuery = "SELECT count(*) FROM " + getTableName(dataSetId) + " as d ";
		countQuery = appendHoldOutCondition(countQuery, holdOutSelector);
		countQuery = appendRowSelectorCondition(countQuery, rowSelector, columnIndices, dataSetId);
		countQuery += ";";

		try (final Statement countStatement = connection.createStatement()) {
			countStatement.setQueryTimeout(20);

			try (final ResultSet resultSet = countStatement.executeQuery(countQuery)) {
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
		final String tableName = getTableName(dataSetId);

		try {
			return existsTable(tableName);
		} catch (final SQLException e) {
			LOGGER.error("The table could not be checked!", e);
			throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.TABLE_CHECk,
			                                              "The table could not be checked!", e);
		}
	}

	/**
	 * Checks if a table with the given name exists.
	 *
	 * @param tableName Name of the table to check.
	 * @return True if the table exists, false if not.
	 * @throws SQLException If the SQL statement could not be executed.
	 */
	private boolean existsTable(final String tableName) throws SQLException {
		final var metaData = connection.getMetaData();

		try (final ResultSet resultSet = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
			if (resultSet.next()) {
				return true;
			}
		}

		try (final ResultSet resultSet = metaData.getTables(null, null, tableName.toUpperCase(),
		                                                    new String[]{"TABLE"})) {
			if (resultSet.next()) {
				return true;
			}
		}

		try (final ResultSet resultSet = metaData.getTables(null, null, tableName.toLowerCase(),
		                                                    new String[]{"TABLE"})) {
			return resultSet.next();
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

	/**
	 * Marks the given process as outdated.
	 * If the process is part of a stage, sets the stage and the following processes to outdated.
	 * The changes are not saved to the database.
	 *
	 * @param process The process to mark as outdated.
	 * @throws BadStateException If the process is running or scheduled.
	 */
	public void markProcessOutdated(final BackgroundProcessEntity process) throws BadStateException {
		if (process.getExternalProcessStatus() == ProcessStatus.RUNNING ||
		    process.getExternalProcessStatus() == ProcessStatus.SCHEDULED) {
			throw new BadStateException(BadStateException.PROCESS_STARTED,
			                            "Process cannot be configured if the it is scheduled or started!");
		}

		if (!targetStatus.contains(process.getExternalProcessStatus())) {
			return;
		}

		process.setExternalProcessStatus(ProcessStatus.OUTDATED);

		if (process instanceof ExternalProcessEntity externalProcessEntity) {
			final ExecutionStepEntity stage = externalProcessEntity.getExecutionStep();

			// Set dependent steps to outdated
			markStageOutdated(stage, process.getJobIndex());

			// Set the following stages as outdated
			final PipelineEntity pipeline = stage.getPipeline();
			for (final ExecutionStepEntity other : pipeline.getStages()) {
				if (other.getStageIndex() > stage.getStageIndex()) {
					markStageOutdated(other, -1);
				}
			}
		}
	}

	/**
	 * Marks the given stage as outdated, starting after the given job index.
	 * Pass a negative index to mark all jobs as outdated.
	 * The changes are not saved to the database.
	 *
	 * @param stage The stage to mark as outdated.
	 * @param startJobIndex Index of the first job to set as outdated.
	 * @throws BadStateException If the stage is running or scheduled.
	 */
	private void markStageOutdated(final ExecutionStepEntity stage, final int startJobIndex) throws BadStateException {
		if (stage.getStatus() == StageStatus.RUNNING) {
			throw new BadStateException(BadStateException.PROCESS_STARTED,
			                            "Process cannot be configured if the it is scheduled or started!");
		}

		if (stage.getStatus() == StageStatus.NOT_STARTED) {
			return;
		}

		boolean outdateProcess = false;
		for (final ExternalProcessEntity other : stage.getProcesses()) {
			if (other.getJobIndex() > startJobIndex) {
				// Ignore SKIPPED processes here, as their state can only outdated directly by markProcessOutdated.
				if (targetStatus.contains(other.getExternalProcessStatus())) {
					if (other.getExternalProcessStatus() != ProcessStatus.SKIPPED) {
						other.setExternalProcessStatus(ProcessStatus.OUTDATED);
						outdateProcess = true;
					}
				} else {
					return;
				}
			}
		}

		if ((startJobIndex >= 0 || outdateProcess) && targetStageStatus.contains(stage.getStatus())) {
			stage.setStatus(StageStatus.OUTDATED);
		}
	}

	private Optional<DataSetEntity> getOriginalDataSetEntity(final ProjectEntity project) {
		return Optional.ofNullable(project.getOriginalData().getDataSet());
	}

	/**
	 * Updates the dataset configuration of the given project with the given DTO.
	 *
	 * @param project              The project to update.
	 * @param datasetConfiguration The dataset configuration to set.
	 */
	private void doUpdateDatasetConfiguration(final ProjectEntity project,
	                                          final DatasetConfiguration datasetConfiguration) {
		datasetConfigurationMapper.updateEntity(project.getOriginalData().getDatasetConfiguration(),
		                                        datasetConfiguration);
		log.debug("Stored dataset configuration");
	}

	private DataSetEntity doStoreOriginalDataConfiguration(ProjectEntity project,
	                                                       final DataConfiguration dataConfiguration) {
		final DataSetEntity dataSetEntity;

		if (project.getOriginalData().getDataSet() == null) {
			dataSetEntity = new DataSetEntity(project.getOriginalData());
		} else {
			dataSetEntity = project.getOriginalData().getDataSet();
		}

		dataSetEntity.setDataConfiguration(dataConfiguration);

		project = projectRepository.save(project);

		log.debug("Stored original data configuration");

		return project.getOriginalData().getDataSet();
	}

	private DataSetEntity doStoreDataConfiguration(final DataConfiguration dataConfiguration,
	                                               final DataProcessingEntity dataProcessingEntity,
	                                               final List<Job> processed)  {
		final DataSetEntity dataSetEntity;

		if (dataProcessingEntity.getDataSet() == null) {
			dataSetEntity = new DataSetEntity(dataProcessingEntity);
		} else {
			dataSetEntity = dataProcessingEntity.getDataSet();
		}

		dataSetEntity.setDataConfiguration(dataConfiguration);
		dataSetEntity.setProcessed(processed);

		log.debug("Stored data configuration for job {}", dataProcessingEntity.getJob().getName());

		return dataSetRepository.save(dataSetEntity);
	}

	private void checkFile(final ProjectEntity project, final DataConfiguration dataConfiguration
	) throws BadStateException, BadDataConfigurationException, InternalIOException {
		// Check if the file and the file configuration are available
		final FileEntity fileEntity = project.getOriginalData().getFile();
		final LobWrapperEntity file = fileEntity.getFile();
		if (file == null) {
			throw new BadStateException(BadStateException.NO_DATASET_FILE,
			                            "Storing a dataset requires the file for the dataset to be selected!");
		}
		final FileConfigurationEntity fileConfiguration = fileEntity.getFileConfiguration();
		if (fileConfiguration == null) {
			throw new BadStateException(BadStateException.NO_DATASET_FILE_CONFIGURATION,
			                            "Storing a dataset requires the file configuration!");
		}

		if (dataConfiguration.getConfigurations().size() != fileEntity.getNumberOfAttributes()) {
			throw new BadDataConfigurationException(BadDataConfigurationException.INVALID_NUMBER_OF_ATTRIBUTES,
			                                        "Dataset contains " + fileEntity.getNumberOfAttributes() +
			                                        " attributes, but the data configuration " +
			                                        dataConfiguration.getConfigurations().size() + " attributes!");
		}

		// Validate that column names match the paths of the FHIR bundle
		final FileType fileType = fileConfiguration.getFileType();
		if (fileType == FileType.FHIR) {
			final List<String> expectedColumns = fhirProcessor.getAttributeNames(file.getLobStream(),
			                                                                     fileConfiguration);

			for (int i = 0; i < fileEntity.getNumberOfAttributes(); i++) {
				final String columnName = dataConfiguration.getConfigurations().get(i).getName();
				final String fhirColumnName = expectedColumns.get(i);
				if (!columnName.equals(fhirColumnName)) {
					throw new BadDataConfigurationException(BadDataConfigurationException.FHIR_ATTRIBUTE_MISMATCH,
					                                        "Attribute number " + (i + 1) + " with name '" + columnName +
					                                        "' does not match the column name of the FHIR bundle '" +
					                                        fhirColumnName + "'");
				}
			}
		}
	}

	private DataSetEntity storeDataSet(final DataSet dataSet, final DataSetEntity dataSetEntity)
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

				// Add values from the dataset, account for rows containing too many values
				int columnIndex = 0;
				final int numberRowsCapped = Math.min(dataRow.getData().size(),
				                                      dataSet.getDataConfiguration().getConfigurations().size());
				for (int i = 0; i < numberRowsCapped; i++) {
					final Data data = dataRow.getData().get(i);
					stringRow.add(convertDataToString(data));
					columnIndex++;
				}

				// Fill missing values with null values to account for rows containing too few values
				for (int i = columnIndex; i < dataSet.getDataConfiguration().getConfigurations().size(); i++) {
					stringRow.add("null");
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

		log.debug("Stored dataset with ID {}", dataSetEntity.getId());

		dataSetEntity.setStoredData(true);
		return dataSetRepository.save(dataSetEntity);
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
			case TEXT -> "'" + data.getValue().toString().replace("'", "''") + "'";
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

		List<Integer> columnIndices;

		if (columnNames.isEmpty()) {
			columnNames = dataConfiguration.getColumnNames();
			columnIndices = null;
		} else {
			existColumnsOrThrow(dataConfiguration, columnNames);

			final List<String> finalColumnNames = columnNames;
			columnIndices = dataConfiguration.getConfigurations()
			                                 .stream()
			                                 .filter(it -> finalColumnNames.contains(it.getName()))
			                                 .map(ColumnConfiguration::getIndex)
			                                 .toList();

			dataConfiguration = extractColumns(dataConfiguration, columnNames);
		}

		// Export the data from the database
		final List<DataRow> dataRows = new ArrayList<>();

		try (final Statement exportStatement = connection.createStatement()) {

			final String exportQuery = createSelectQuery(dataSetEntity.getId(), rowSelector, columnNames, columnIndices,
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
						data.add(convertResultToData(resultSet, dataConfiguration.getConfigurations().size() + 1,
						                             DataType.INTEGER));
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
		} catch (JacksonException e) {
			throw new InternalIOException(InternalIOException.DATA_CONFIGURATION_DESERIALIZATION,
			                              "Failed to export data configuration because of a failed deserialization!",
			                              e);
		}
	}

	private String createSelectQuery(final Long dataSetId, final RowSelector rowSelector,
	                                 final List<String> columnNames, final Collection<Integer> columnIndices,
	                                 final HoldOutSelector holdOutSelector, final boolean pagination,
	                                 final int startRow, final int pageSize, final boolean exportRowIndexColumn) {
		final List<String> quotedColumnNames = columnNames.stream().map(this::quoteColumnName)
		                                                  .collect(Collectors.toCollection(ArrayList::new));
		if (exportRowIndexColumn) {
			quotedColumnNames.add("\"" + DataschemeGenerator.ROW_INDEX_NAME + "\"");
		}

		String query = "SELECT " + String.join(",", quotedColumnNames) + " FROM " + getTableName(dataSetId) + " d";
		query = appendHoldOutCondition(query, holdOutSelector);
		query = appendRowSelectorCondition(query, rowSelector, columnIndices, dataSetId);

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
					return new IntegerData((Integer) resultSet.getObject(columnIndex));
				}
				case TEXT -> {
					return new TextData(resultSet.getString(columnIndex));
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

	/**
	 * Checks if the dataset is already stored.
	 * Throws an exception if the dataset is already stored.
	 *
	 * @param dataSet The dataset to check.
	 * @throws BadDataSetIdException If the dataset is already stored.
	 */
	private void throwIfStored(@Nullable final DataSetEntity dataSet) throws BadDataSetIdException {
		if (dataSet == null) {
			return;
		}

		if (dataSet.isStoredData()) {
			throw new BadDataSetIdException(BadDataSetIdException.ALREADY_STORED, "The data has already been stored!");
		}
	}

	/**
	 * Checks if the dataset is already stored and confirmed.
	 *
	 * @param dataSet The dataset to check.
	 * @throws BadDataSetIdException If the dataset is confirmed.
	 */
	private void throwIfConfirmed(@Nullable final DataSetEntity dataSet) throws BadDataSetIdException {
		if (dataSet == null) {
			return;
		}

		if (dataSet.isConfirmedData()) {
			throw new BadDataSetIdException(BadDataSetIdException.ALREADY_CONFIRMED,
			                                "The data has already been confirmed!");
		}
	}

	/**
	 * Checks if the data set for the given step has been confirmed.
	 * Otherwise, deletes the data set if present.
	 *
	 * @param dataSet The data set to be deleted.
	 * @throws BadDataSetIdException               If the data is confirmed.
	 * @throws InternalDataSetPersistenceException If the data set could not be deleted.
	 */
	@Transactional
	protected void deleteDataSetIfNotConfirmedOrThrow(@Nullable final DataSetEntity dataSet)
			throws BadDataSetIdException, InternalDataSetPersistenceException {
		throwIfConfirmed(dataSet);
		deleteDataSet(dataSet);
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

	/**
	 * Appends the where condition for filtering by the existence of errors in the given columns.
	 *
	 * @param query         The existing query to be appended.
	 * @param rowSelector   The row selector.
	 * @param columnIndices Indices of the columns which should contain errors. If null, all columns are considered.
	 * @param dataSetId     The ID of the dataset.
	 * @return The appended query.
	 */
	private String appendRowSelectorCondition(String query, final RowSelector rowSelector,
	                                          @Nullable final Collection<Integer> columnIndices, final Long dataSetId) {
		switch (rowSelector) {
			case ALL -> {}
			case VALID -> {
				query = appendWhere(query);
				query += "NOT EXISTS ";
				query = appendTransformationErrorCondition(query, columnIndices, dataSetId);
			}
			case ERRORS -> {
				query = appendWhere(query);
				query += "EXISTS ";
				query = appendTransformationErrorCondition(query, columnIndices, dataSetId);
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

	/**
	 * Returns general information about the given data configuration.
	 *
	 * @param dataConfiguration The data configuration.
	 * @return The information.
	 */
	private DataConfigurationInfo getDataConfigurationInfo(final DataConfiguration dataConfiguration) {
		int numberColumns = 0;
		int numberNumericColumns = 0;
		int numberCategoricalColumns = 0;
		int numberDateColumns = 0;

		for (final ColumnConfiguration columnConfiguration : dataConfiguration.getConfigurations()) {
			numberColumns++;

			if (columnConfiguration.getScale() != null) {
				switch (columnConfiguration.getScale()) {
					case DATE -> numberDateColumns++;
					case NOMINAL -> numberCategoricalColumns++;
					case ORDINAL, INTERVAL, RATIO -> numberNumericColumns++;
				}
			}
		}

		return new DataConfigurationInfo(numberColumns, numberNumericColumns, numberCategoricalColumns,
		                                 numberDateColumns);
	}

	/**
	 * Counts the number of rows in the given dataset that are retained from the corresponding original dataset.
	 *
	 * @param dataSet The dataset.
	 * @return The number of retained rows.
	 * @throws BadStateException                   If the corresponding project has no original dataset.
	 * @throws InternalDataSetPersistenceException If executing the query failed.
	 */
	private int getNumberOfRetainedRows(
			final DataSetEntity dataSet) throws BadStateException, InternalDataSetPersistenceException {
		final DataSetEntity original = dataSet.getProject().getOriginalData().getDataSet();
		if (original == null) {
			throw new BadStateException(BadStateException.NO_DATA_SET, "No original dataset for comparison available!");
		}

		final String originalTableName = getTableName(original.getId());
		final String otherTableName = getTableName(dataSet.getId());

		final String tableA = "f";
		final String tableB = "s";
		final String joinPart = getJoinStatement(original.getDataConfiguration(), tableA, tableB);

		final String query =
				"""
				WITH original_table_filtered AS (
				    SELECT *, ROW_NUMBER() OVER (ORDER BY %s) - 1 as row_number
				    FROM %s
				    WHERE %s = false
				)
				SELECT COUNT(*) as matching_rows
				FROM original_table_filtered %s
				JOIN %s %s %s
				    AND %s.row_number = %s.%s;
				""".formatted(DataschemeGenerator.ROW_INDEX_NAME, originalTableName,
				              DataschemeGenerator.HOLD_OUT_FLAG_NAME, tableA, otherTableName, tableB, joinPart, tableA,
				              tableB, DataschemeGenerator.ROW_INDEX_NAME);

		try (final Statement countStatement = connection.createStatement()) {
			try (ResultSet resultSet = countStatement.executeQuery(query)) {
				resultSet.next();
				return resultSet.getInt(1);
			}
		} catch (SQLException e) {
			throw new InternalDataSetPersistenceException(InternalDataSetPersistenceException.DATASET_COMPARISON,
			                                              "Failed to compare rows for dataset with ID '" +
			                                              dataSet.getId() + "'!", e);
		}
	}

	/**
	 * Creates the join statements for joining two datasets on all columns of the dataset.
	 *
	 * @param dataConfiguration The data configuration that contains the column names.
	 * @param tableA            Alias for the first table.
	 * @param tableB            Alias for the second table.
	 * @return The join statement.
	 */
	private String getJoinStatement(final DataConfiguration dataConfiguration, final String tableA,
	                                final String tableB) {
		final StringBuilder joinStatement = new StringBuilder();

		String firstColumn = dataConfiguration.getConfigurations().get(0).getName();
		firstColumn = quoteColumnName(firstColumn);
		joinStatement.append(" ON ")
		             .append(tableA).append(".").append(firstColumn)
		             .append(" = ")
		             .append(tableB).append(".").append(firstColumn);

		for (int i = 1; i < dataConfiguration.getConfigurations().size(); ++i) {
			String columnName = dataConfiguration.getConfigurations().get(i).getName();
			columnName = quoteColumnName(columnName);
			joinStatement.append(" AND ")
			             .append(tableA).append(".").append(columnName)
			             .append(" = ")
			             .append(tableB).append(".").append(columnName);
		}

		return joinStatement.toString();
	}

	/**
	 * Puts the given column name to allow reserved keywords.
	 *
	 * @param columnName The column name.
	 * @return The quoted name.
	 */
	final String quoteColumnName(final String columnName) {
		return "\"" + columnName + "\"";
	}

	/**
	 * Appends a query checking the existing for transformation errors in the given columns of the dataset with the given ID.
	 *
	 * @param query         The query to be appended.
	 * @param columnIndices Indices of the columns to be considered. If null, all columns are considered.
	 * @param dataSetId     The dataset ID.
	 * @return The appended query.
	 */
	private String appendTransformationErrorCondition(String query, @Nullable final Collection<Integer> columnIndices,
	                                                  final long dataSetId) {
		query += "(SELECT 1 FROM data_transformation_error_entity e WHERE e.data_set_id = " + dataSetId +
		         " AND e.row_index = d." + DataschemeGenerator.ROW_INDEX_NAME;

		if (columnIndices != null) {
			query += " AND e.column_index IN (" +
			         String.join(",", columnIndices.stream().map(Object::toString).toList()) + ")";
		}

		query += ")";

		return query;
	}

}
