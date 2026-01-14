package de.kiaim.cinnamon.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.model.dto.ErrorRequest;
import de.kiaim.cinnamon.model.dto.ExternalProcessResponse;
import de.kiaim.cinnamon.model.serialization.mapper.JsonMapper;
import de.kiaim.cinnamon.model.serialization.mapper.YamlMapper;
import de.kiaim.cinnamon.model.status.synthetization.SynthetizationStatus;
import de.kiaim.cinnamon.platform.config.SerializationConfig;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.configuration.*;
import de.kiaim.cinnamon.platform.model.entity.*;
import de.kiaim.cinnamon.platform.model.enumeration.DataSetSelector;
import de.kiaim.cinnamon.platform.model.enumeration.HoldOutSelector;
import de.kiaim.cinnamon.platform.model.enumeration.ProcessStatus;
import de.kiaim.cinnamon.platform.model.TransformationResult;
import de.kiaim.cinnamon.platform.model.file.CsvFileConfiguration;
import de.kiaim.cinnamon.platform.model.file.FileType;
import de.kiaim.cinnamon.platform.processor.CsvProcessor;
import de.kiaim.cinnamon.platform.processor.DataProcessor;
import de.kiaim.cinnamon.platform.repository.BackgroundProcessRepository;
import de.kiaim.cinnamon.platform.repository.ProjectRepository;
import io.netty.channel.ChannelOption;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import reactor.netty.http.client.HttpClient;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service class for managing processes.
 */
@Service
@Slf4j
public class ProcessService {

	private static final String PROCESS_ID_PLACEHOLDER = "PROCESS_ID";

	private final int port;

	private final ObjectMapper jsonMapper;
	private final ObjectMapper yamlMapper;

	private final CinnamonConfiguration cinnamonConfiguration;

	private final BackgroundProcessRepository backgroundProcessRepository;
	private final ProjectRepository projectRepository;

	private final CsvProcessor csvProcessor;
	private final DatabaseService databaseService;
	private final DataProcessorService dataProcessorService;
	private final DataSetService dataSetService;
	private final ExternalServerInstanceService externalServerInstanceService;
	private final HttpService httpService;
	private final StepService stepService;

	public ProcessService(final SerializationConfig serializationConfig, @Value("${server.port}") final int port,
	                      final CinnamonConfiguration cinnamonConfiguration,
	                      final BackgroundProcessRepository backgroundProcessRepository,
	                      final ProjectRepository projectRepository, final CsvProcessor csvProcessor,
	                      final DatabaseService databaseService, final DataProcessorService dataProcessorService,
	                      final DataSetService dataSetService,
	                      final ExternalServerInstanceService externalServerInstanceService,
	                      final HttpService httpService, final StepService stepService
	) {
		this.jsonMapper = serializationConfig.jsonMapper();
		this.yamlMapper = serializationConfig.yamlMapper();

		this.port = port;
		this.cinnamonConfiguration = cinnamonConfiguration;
		this.backgroundProcessRepository = backgroundProcessRepository;
		this.projectRepository = projectRepository;
		this.csvProcessor = csvProcessor;
		this.databaseService = databaseService;
		this.dataProcessorService = dataProcessorService;
		this.dataSetService = dataSetService;
		this.externalServerInstanceService = externalServerInstanceService;
		this.httpService = httpService;
		this.stepService = stepService;
	}

	/**
	 * Updates and returns the process status of the pipeline.
	 * If a process is running, the status of that process will be fetched from the external server.
	 * If fetching the status fails,the process will be set to error and the next process of the same job will be started.
	 *
	 * @param project The project.
	 * @return The updated pipeline.
	 * @throws InternalInvalidStateException If a running process has no server instance assigned.
	 */
	@Transactional
	public PipelineEntity getPipeline(final ProjectEntity project) throws InternalInvalidStateException {
		final PipelineEntity pipeline = project.getPipelines().get(0);

		for (final ExecutionStepEntity stage : pipeline.getStages()) {
			if (stage.getStatus() == ProcessStatus.RUNNING) {
				getStatus(project, stage.getStage());
				break;
			}
		}

		return pipeline;
	}

	/**
	 * Updates and returns the process status of the given step in the given project.
	 * If a process is running, the status of that process will be fetched from the external server.
	 * If fetching the status fails,the process will be set to error and the next process of the same job will be started.
	 *
	 * @param project The project.
	 * @param stage   The stage.
	 * @return The updated execution.
	 * @throws InternalInvalidStateException If the process has no server instance.
	 */
	@Transactional
	public ExecutionStepEntity getStatus(final ProjectEntity project, final Stage stage) throws InternalInvalidStateException {
		final var executionStep = project.getPipelines().get(0).getStageByStep(stage);

		if (executionStep.getStatus() == ProcessStatus.RUNNING) {
			final var process = executionStep.getCurrentProcess();

			try {
				updateProcessStatus(process);
			} catch (final InternalRequestException e) {
				final ExternalServerInstance instance = stepService.getExternalServerInstanceConfiguration(
						process.getServerInstance());

				setProcessError(executionStep, e.getMessage());

				// Start the next process of the same step
				startScheduledProcess(process.getJob().getEndpoint(), instance);
			}

			projectRepository.save(project);
		}

		return executionStep;
	}

	/**
	 * Configures the job by setting the configuration and the skip flag.
	 * Currently, always uses the first configuration for the job.
	 * If skip is true, errors for missing configurations are ignored.
	 * Marks the job as outdated if something changed.
	 *
	 * @param project The project.
	 * @param job     The job to be configured.
	 * @param skip    If the job should be skipped.
	 * @throws BadStateException             If the corresponding process is already running or scheduled.
	 * @throws InternalInvalidStateException If the process entity is missing.
	 */
	@Transactional
	public void configureProcess(final ProjectEntity project, final Job job, final boolean skip)
			throws BadStateException, InternalInvalidStateException {
		// Get process entity
		final Optional<ExternalProcessEntity> optional = project.getPipelines().get(0).getStageByJob(job);;
		if (optional.isEmpty()) {
			throw new InternalInvalidStateException(InternalInvalidStateException.MISSING_PROCESS_ENTITY,
			                                        "No process entity for step '" + job.getName() + "' available!");
		}
		final ExternalProcessEntity externalProcess = optional.get();

		if (externalProcess.getExternalProcessStatus() == ProcessStatus.SCHEDULED ||
		    externalProcess.getExternalProcessStatus() == ProcessStatus.RUNNING) {
			throw new BadStateException(BadStateException.PROCESS_STARTED,
			                            "Process cannot be configured if the it is scheduled or started!");
		}

		boolean markAsOutdated = false;

		try {
			// Set the configuration
			ConfigurationListEntity configurationList = project.getConfigurationList(job.getEndpoint().getConfiguration());
			if (configurationList == null || configurationList.getConfigurations().isEmpty()) {
				throw new BadStateException(BadStateException.CONFIGURATION,
				                            "No configuration '" +
				                            job.getEndpoint().getConfiguration().getConfigurationName() + "' for job '" +
				                            job.getName() + "'!");
			}

			BackgroundProcessConfiguration config = configurationList.getConfigurations().get(0);

			if (config.getProcessUrl() == null || config.getProcessUrl().isBlank()) {
				throw new BadStateException(BadStateException.CONFIGURATION,
				                            "No URL for configuration '" +
				                            job.getEndpoint().getConfiguration().getConfigurationName() + "' for job '" +
				                            job.getName() + "'!");
			}

			if (externalProcess.getConfiguration() == null) {
				externalProcess.setConfiguration(config);
				markAsOutdated = true;
			}
		} catch (final ApiException apiException) {
			if (!skip) {
				throw apiException;
			}
		}

		// Set if the process should be skipped
		if (skip != externalProcess.isSkip()) {
			externalProcess.setSkip(skip);
			markAsOutdated = true;
		}

		if (markAsOutdated) {
			databaseService.markProcessOutdated(externalProcess);
		}

		// Save project
		projectRepository.save(project);
	}

	/**
	 * Starts the execution of the given stage in the given project.
	 * If the job is provided, starts the stage beginning from this job, otherwise start the stage beginning on the first job.
	 *
	 * @param project The project the process corresponds to.
	 * @param stage   The step the process corresponds to.
	 * @throws BadDataSetIdException                     If no DataConfiguration is associated with the given project.
	 * @throws BadStateException                         If no original data set exist.
	 * @throws BadStepNameException                      If the given job is not part of the given stage.
	 * @throws InternalApplicationConfigurationException If the given step is not configured.
	 * @throws InternalDataSetPersistenceException       If the data set could not be exported due to an internal error.
	 * @throws InternalInvalidStateException             If no ExternalProcessEntity exists for the given step.
	 *                                                   If a finished process does not contain a dataset.
	 * @throws InternalIOException                       If the request body could not be created.
	 * @throws InternalMissingHandlingException          If no implementation exists for a valid configuration.
	 * @throws InternalRequestException                  If the request to the external server for starting the process failed.
	 */
	@Transactional
	public ExecutionStepEntity start(final ProjectEntity project, final Stage stage, @Nullable final Job job)
			throws BadDataSetIdException, BadStateException, BadStepNameException, InternalApplicationConfigurationException, InternalDataSetPersistenceException, InternalInvalidStateException, InternalIOException, InternalMissingHandlingException, InternalRequestException {
		final var executionStep = project.getPipelines().get(0).getStageByStep(stage);

		if (executionStep.getStatus() == ProcessStatus.RUNNING) {
			return executionStep;
		}

		executionStep.setStatus(ProcessStatus.RUNNING);

		// Start the first step
		try {
			if (job != null) {
				startJob(executionStep, job);
			} else {
				// Reset status from potential previous execution
				for (final ExternalProcessEntity externalProcessEntity : executionStep.getProcesses()) {
					resetProcess(externalProcessEntity);
				}

				startNext(executionStep);
			}
		} catch (final Exception e) {
			setProcessError(executionStep, e.getMessage());
			throw e;
		} finally {
			projectRepository.save(project);
		}

		return executionStep;
	}

	/**
	 * Cancels the execution of the given stage in the given project.
	 *
	 * @param project The project.
	 * @param stage   The step.
	 * @return The updated execution entity.
	 * @throws InternalApplicationConfigurationException If the step is not configured.
	 * @throws InternalInvalidStateException If the process has no server instance.
	 */
	@Transactional
	public ExecutionStepEntity cancel(final ProjectEntity project, final Stage stage)
			throws InternalApplicationConfigurationException, InternalInvalidStateException {
		final var executionStep = project.getPipelines().get(0).getStageByStep(stage);

		if (executionStep.getStatus() == ProcessStatus.RUNNING) {
			// Cancel the current process
			cancelProcess(executionStep.getCurrentProcess());

			// Update
			executionStep.setStatus(ProcessStatus.CANCELED);
			executionStep.setCurrentProcessIndex(null);

			projectRepository.save(project);
		}

		return executionStep;
	}

	/**
	 * Deletes the pipeline of the given project by deleting all stages.
	 *
	 * @param project The project.
	 * @throws BadStateException                   If a process of the stage is running.
	 * @throws InternalDataSetPersistenceException If a dataset table could not be deleted.
	 */
	public void deletePipeline(final ProjectEntity project)
			throws InternalDataSetPersistenceException, BadStateException {
		final PipelineEntity pipeline = project.getPipelines().get(0);
		for (final ExecutionStepEntity stage : pipeline.getStages()) {
			deleteStage(stage);
		}
		projectRepository.save(project);
	}

	/**
	 * Resets the given and all following stages by deleting all results and resetting the status.
	 *
	 * @param project The project.
	 * @param stage   The step.
	 * @return The updated execution entity.
	 * @throws BadStateException                   If a process of the stage is running.
	 * @throws InternalDataSetPersistenceException If a dataset table could not be deleted.
	 */
	@Transactional
	public ExecutionStepEntity deleteStage(final ProjectEntity project, final Stage stage)
			throws BadStateException, InternalDataSetPersistenceException {
		// Check if the pipeline contains the given stage.
		final ExecutionStepEntity executionStep = project.getPipelines().get(0).getStageByStep(stage);

		for (final ExecutionStepEntity s : project.getPipelines().get(0).getStages()) {
			deleteStage(s);
			if (s.getStage().equals(stage)) {
				break;
			}
		}

		projectRepository.save(project);
		return executionStep;
	}

	/**
	 * Finishes the process with the given process ID.
	 * Either resultFiles or errorRequest has to be not null.
	 * <p>
	 * If resultFiles is not null, checks if they contain an error output as defined in the configuration.
	 * If no error message is present, sets the status to 'finished'
	 * and starts the next process of the stage as well as the next scheduled process of the same job.
	 * If an error is present, aborts the current execution step and stets the status to 'error'.
	 * <p>
	 * If errorRequest is not null, treats the process as failed.
	 *
	 * @param processId   The ID of the process to finish.
	 * @param resultFiles All files send in the callback request.
	 * @throws BadProcessIdException If the given process ID is not valid.
	 * @throws InternalInvalidStateException If the process has no server instance.
	 */
	@Transactional
	public void finishProcess(final UUID processId,
	                          @Nullable final Set<Map.Entry<String, MultipartFile>> resultFiles,
	                          @Nullable final ErrorRequest errorRequest)
			throws BadProcessIdException, InternalInvalidStateException {
		final Optional<BackgroundProcessEntity> backgroundProcessOptional = backgroundProcessRepository.findByUuid(
				processId);
		// Invalid processID
		if (backgroundProcessOptional.isEmpty()) {
			throw new BadProcessIdException(BadProcessIdException.NO_PROCESS,
			                                "No process with the given ID '" + processId +
			                                "' exists!");
		}
		final var process = backgroundProcessOptional.get();
		final ExternalServerInstance instance = stepService.getExternalServerInstanceConfiguration(
				process.getServerInstance());

		// Finish the current process
		final boolean containsError = tryFinishProcess(process, resultFiles, errorRequest);

		if (process instanceof ExternalProcessEntity externalProcess) {
			// Start the next step of this process
			if (!containsError) {
				tryStartNext(externalProcess);
			}

			// Start the next process of the same step
			startScheduledProcess(externalProcess.getJob().getEndpoint(), instance);
		}

	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected boolean tryFinishProcess(final BackgroundProcessEntity process,
	                                   @Nullable final Set<Map.Entry<String, MultipartFile>> resultFiles,
	                                   @Nullable final ErrorRequest errorRequest
	) {
		boolean containsError;

		try {
			containsError = doFinishProcess(process, resultFiles, errorRequest);
		} catch (final Exception e) {
			log.error("Failed to finish process!", e);
			setProcessError(process, e.getMessage());
			containsError = true;
		}

		process.setUuid(null);

		final ProjectEntity project = process.getProject();
		projectRepository.save(project);

		return containsError;
	}


	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected void tryStartNext(final ExternalProcessEntity process) {
		try {
			startNext(process.getExecutionStep());
		} catch (final Exception e) {
			log.error("Failed to start process!", e);
		}

		final ProjectEntity project = process.getProject();
		projectRepository.save(project);
	}

	/**
	 * Finishes the given process.
	 *
	 * @param process      The process to finish.
	 * @param resultFiles  All files sent in the callback request.
	 * @param errorRequest Error sent back in case the process failed.
	 * @return If the process has been finished.
	 * @throws BadDataConfigurationException             If the data configuration is not valid.
	 * @throws BadDatasetException                     If the data set contains a row with too few or too many values.
	 * @throws BadDataSetIdException                     If the data set could not be exported.
	 * @throws BadStateException                         If the file for the dataset has not been stored.
	 * @throws InternalApplicationConfigurationException If the step is not configured.
	 * @throws InternalDataSetPersistenceException       If the data set could not be exported.
	 * @throws InternalInvalidStateException             If no ExternalProcessEntity exists for the given step.
	 *                                                   If the process has no server instance.
	 * @throws InternalIOException                       If a result file could not be read.
	 * @throws InternalMissingHandlingException          If no handling exists for the selector of the process.
	 * @throws InternalRequestException                  If the request to the external server for starting the process failed.
	 */
	protected boolean doFinishProcess(final BackgroundProcessEntity process,
	                                  @Nullable final Set<Map.Entry<String, MultipartFile>> resultFiles,
	                                  @Nullable final ErrorRequest errorRequest
	) throws BadDataConfigurationException, BadDatasetException, BadDataSetIdException, BadStateException, InternalApplicationConfigurationException, InternalDataSetPersistenceException, InternalInvalidStateException, InternalIOException, InternalMissingHandlingException, InternalRequestException {

		final var endpoint = cinnamonConfiguration.getExternalServerEndpoints().get(process.getEndpoint());

		ExternalProcessEntity externalProcess = null;
		DataProcessingEntity dataProcessing = null;
		if (process instanceof ExternalProcessEntity ep) {
			externalProcess = ep;

			if (process instanceof DataProcessingEntity) {
				dataProcessing = (DataProcessingEntity) process;
			}
		}

		final var files = process.getResultFiles();
		files.clear();

		boolean containsError = false;
		String errorMessage = null;

		List<Job> processed;
		if (externalProcess != null) {
			var input = dataSetService.getDataSet(externalProcess);
			processed = new ArrayList<>(input.getProcessed());
			processed.add(externalProcess.getJob());
		} else {
			processed = new ArrayList<>();
		}

		if (errorRequest != null) {
			containsError = true;
			errorMessage = errorRequest.getErrorMessage();
		} else if (resultFiles != null) {

			for (final var entry : resultFiles) {
				try {
					final var value = entry.getValue();

					final var output = getStepOutputConfiguration(endpoint, entry.getKey());

					if (output == null) {
						// If nothing is specified, save as LOB
						files.put(value.getOriginalFilename(), new LobWrapperEntity(value.getBytes()));
					} else {
						switch (output.getEncoding()) {
							case DATA -> {
								final FileConfigurationEntity fileConfigurationEntity = new CsvFileConfigurationEntity(
										new CsvFileConfiguration());

								final DataConfiguration resultDataConfiguration = process.getProject().getOriginalData()
								                                                         .getDataSet()
								                                                         .getDataConfiguration();
								// TODO estimate config if we enable data type changes
//								final DataConfiguration resultDataConfiguration = csvProcessor.estimateDataConfiguration(
//										value.getInputStream(), fileConfigurationEntity,
//										DatatypeEstimationAlgorithm.MOST_GENERAL);
								final TransformationResult transformationResult = csvProcessor.read(
										value.getInputStream(),
										fileConfigurationEntity,
										resultDataConfiguration);
								try {
									databaseService.storeTransformationResult(transformationResult, dataProcessing,
									                                          processed);
								} catch (final BadDataConfigurationException e) {
									throw new InternalInvalidResultException(
											InternalInvalidResultException.INVALID_ESTIMATION,
											"Estimation created an invalid configuration!", e);
								}

							}
							case DATA_SET -> {
								String jsonString = IOUtils.toString(value.getInputStream(), StandardCharsets.UTF_8);
								DataSet dataSet = jsonMapper.readValue(jsonString, DataSet.class);

								TransformationResult transformationResult = new TransformationResult(dataSet,
								                                                                     new ArrayList<>());
								databaseService.storeTransformationResult(transformationResult, dataProcessing,
								                                          processed);
							}
							case ERROR -> {
								containsError = true;
								var errorRequestPart = jsonMapper.readValue(value.getBytes(), ErrorRequest.class);
								errorMessage = errorRequestPart.getErrorMessage();
							}
							case ERROR_MESSAGE -> {
								containsError = true;
								errorMessage = new String(value.getBytes());
							}
							case FILE -> {
								files.put(value.getOriginalFilename(), new LobWrapperEntity(value.getBytes()));
							}
						}
					}

				} catch (final IOException | InternalInvalidResultException e) {
					throw new InternalIOException(InternalIOException.MULTIPART_READING,
					                              "Failed to read result file '" + entry.getKey() + "'!", e);
				}
			}
		}

		// Hardcoded fix for synthetization callback status
		if (externalProcess != null && externalProcess.getJob().isFixStatus()) {
			try {
				updateProcessStatus(externalProcess);
				final var synthStatus = jsonMapper.readValue(externalProcess.getStatus(), SynthetizationStatus.class);
				for (final var abc : synthStatus.getStatus()) {
					abc.setCompleted("True");
				}
				externalProcess.setStatus(jsonMapper.writeValueAsString(synthStatus));
			} catch (JsonProcessingException e) {
				log.warn("Failed to update detailed status!", e);
			}
		}

		if (containsError) {
			setProcessError(process, errorMessage);
		} else {
			process.setExternalProcessStatus(ProcessStatus.FINISHED);
			process.setServerInstance(null);
		}

		return containsError;
	}

	@Nullable
	private StepOutputConfiguration getStepOutputConfiguration(final ExternalEndpoint endpoint, final String partName) {
		for (final var output : endpoint.getOutputs()) {
			if (output.getPartName().equals(partName)) {
				return output;
			}
		}
		return null;
	}

	/**
	 * Returns the process in the given project for the step with the given name.
	 * If the process is running, updates the status by calling the external API.
	 *
	 * @param externalProcess The process which status should be updated.
	 * @throws InternalInvalidStateException If the process has no server instance.
	 * @throws InternalRequestException If the request for the status fails.
	 */
	private void updateProcessStatus(final ExternalProcessEntity externalProcess)
			throws InternalRequestException, InternalInvalidStateException {
		if (externalProcess.getExternalProcessStatus() == ProcessStatus.RUNNING) {
			fetchStatus(externalProcess);
		}
	}

	/**
	 * Starts the given external process if resources are available.
	 * If no resources are available, the process will be scheduled and started if resources are available.
	 *
	 * @param externalProcess The process to be started.
	 * @throws BadStateException                   If no original data set exist.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported.
	 * @throws InternalInvalidStateException       If no ExternalProcessEntity exists for the given step.
	 *                                             If a finished process does not contain data set.
	 * @throws InternalIOException                 If the request could not be created.
	 * @throws InternalMissingHandlingException    If no implementation exists for a valid configuration.
	 * @throws InternalRequestException            If the request to the external server for starting the process failed.
	 */
	public void startOrScheduleBackendProcess(final BackgroundProcessEntity externalProcess)
			throws BadStateException, InternalDataSetPersistenceException, InternalInvalidStateException, InternalIOException, InternalMissingHandlingException, InternalRequestException {
		// Get configuration
		final ExternalEndpoint endpoint = cinnamonConfiguration.getExternalServerEndpoints()
		                                                       .get(externalProcess.getEndpoint());
		final ExternalServer server = endpoint.getServer();

		final ExternalServerInstance instance = externalServerInstanceService.findAvailableExternalServerInstance(server, false);
		if (instance != null) {
			doStartBackgroundProcess(externalProcess, instance);
		} else {
			scheduleProcess(externalProcess);
		}
	}

	/**
	 * Starts the given external process if resources are available.
	 * If no resources are available, the process will be scheduled and started if resources are available.
	 *
	 * @param externalProcess The process to be started.
	 * @throws BadStateException                   If no original data set exist.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported.
	 * @throws InternalInvalidStateException       If no ExternalProcessEntity exists for the given step.
	 *                                             If a finished process does not contain data set.
	 * @throws InternalIOException                 If the request could not be created.
	 * @throws InternalMissingHandlingException    If no implementation exists for a valid configuration.
	 * @throws InternalRequestException            If the request to the external server for starting the process failed.
	 */
	private void startOrScheduleProcess(final ExternalProcessEntity externalProcess)
			throws InternalDataSetPersistenceException, InternalRequestException, InternalIOException, InternalInvalidStateException, BadStateException, InternalMissingHandlingException {
		if (externalProcess.getExternalProcessStatus() == ProcessStatus.SCHEDULED ||
		    externalProcess.getExternalProcessStatus() == ProcessStatus.RUNNING) {
			return;
		}

		try {
			final String configuration = externalProcess.getConfigurationString();
			if (configuration == null) {
				throw new InternalInvalidStateException(InternalInvalidStateException.MISSING_CONFIGURATION,
				                                        "No configuration for step '" + externalProcess.getJob().getName() +
				                                        "' found!");
			}

			startOrScheduleBackendProcess(externalProcess);

		} catch (final Exception e) {
			setProcessError(externalProcess, e.getMessage());
			throw e;
		}
	}


	/**
	 * Cancels the given process.
	 *
	 * @param backgroundProcess The process to be canceled.
	 * @throws InternalInvalidStateException If the process has no server instance.
	 */
	@Transactional
	public void cancelProcess(final BackgroundProcessEntity backgroundProcess) throws InternalInvalidStateException {
		if (!(backgroundProcess.getExternalProcessStatus() == ProcessStatus.SCHEDULED ||
		      backgroundProcess.getExternalProcessStatus() == ProcessStatus.RUNNING)) {
			return;
		}

		// Get configuration
		final ExternalEndpoint ese = stepService.getExternalServerEndpointConfiguration(backgroundProcess);
		final ExternalServerInstance esi = stepService.getExternalServerInstanceConfiguration(backgroundProcess.getServerInstance());

		if (backgroundProcess.getExternalProcessStatus() == ProcessStatus.SCHEDULED) {
			backgroundProcess.setScheduledTime(null);
		} else if (!ese.getCancelEndpoint().isBlank()) {

			final String serverUrl = esi.getUrl();
			final String cancelEndpoint = injectUrlParameter(ese.getCancelEndpoint(), backgroundProcess);

			final MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
			formData.add("session_key", backgroundProcess.getUuid().toString());
			formData.add("pid", backgroundProcess.getExternalId());

			// Do the request
			final WebClient webClient = WebClient.builder().baseUrl(serverUrl).build();
			webClient.method(ese.getCancelHttpMethod().asHttpMethod())
			         .uri(cancelEndpoint)
			         .body(BodyInserters.fromFormData(formData))
			         .retrieve()
			         .onStatus(HttpStatusCode::isError,
			                   b -> {
				                   log.warn("Failed to cancel the process! Got status of {}", b.statusCode());
				                   return null;
			                   })
			         .toBodilessEntity()
			         .onErrorComplete()
			         .block();
		}

		backgroundProcess.setExternalProcessStatus(ProcessStatus.CANCELED);
		backgroundProcess.setServerInstance(null);
		startScheduledProcess(ese, esi);

		backgroundProcessRepository.save(backgroundProcess);
	}

	/**
	 * Starts the stage beginning from the given job.
	 * Results of the following jobs are cleared.
	 *
	 * @param executionStep The stage.
	 * @param job The job to start.
	 * @throws BadStateException                   If no original data set exist.
	 * @throws BadStepNameException                If the given job is not part of the given stage.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported.
	 * @throws InternalInvalidStateException       If no ExternalProcessEntity exists for the given step.
	 *                                             If a finished process does not contain data set.
	 * @throws InternalIOException                 If the request could not be created.
	 * @throws InternalMissingHandlingException    If no implementation exists for a valid configuration.
	 * @throws InternalRequestException            If the request to the external server for starting the process failed.
	 *
	 */
	private void startJob(final ExecutionStepEntity executionStep, final Job job)
			throws BadStateException, BadStepNameException, InternalDataSetPersistenceException, InternalIOException, InternalInvalidStateException, InternalMissingHandlingException, InternalRequestException  {

		ExternalProcessEntity process = null;
		boolean foundNext = false;

		for (final var processCandidate : executionStep.getProcesses()) {
			if (process == null && processCandidate.getJob().getName().equals(job.getName())) {
				process = processCandidate;
			}

			if (process == null) {
				// Validate that preceding jobs are finished or skipped
				if (processCandidate.getExternalProcessStatus() != ProcessStatus.FINISHED &&
				    processCandidate.getExternalProcessStatus() != ProcessStatus.SKIPPED) {
					throw new BadStateException(BadStateException.PRECEDING_JOB_NOT_FINISHED,
					                            "The preceding job '" + processCandidate.getJob().getName() +
					                            "' is not finished or skipped!");
				}
			} else {
				// Reset status from potential previous execution for the following steps
				resetProcess(processCandidate);

				if (!foundNext) {
					if (processCandidate.isSkip()) {
						processCandidate.setExternalProcessStatus(ProcessStatus.SKIPPED);
					} else {
						// Check if a hold-out split is required and present.
						final boolean requiresHoldOut = stepService.requiresHoldOutSplit(processCandidate.getJob());
						final boolean hasHoldOut = processCandidate.getProject().getOriginalData().isHasHoldOut();

						if (requiresHoldOut && !hasHoldOut) {
							processCandidate.setExternalProcessStatus(ProcessStatus.SKIPPED);
							processCandidate.setStatus("The process requires a hold out split, but no hold out split is present!");
						} else {
							foundNext = true;
						}
					}
				}
			}
		}

		if (process == null) {
			throw new BadStepNameException(BadStepNameException.NOT_IN_STAGE,
			                               "Job " + job.getName() + " is not contained in stage " +
			                               executionStep.getStage().getStageName());
		}

		if (foundNext) {
			executionStep.setCurrentProcessIndex(process.getJobIndex());
			startOrScheduleProcess(process);
		} else {
			executionStep.setStatus(ProcessStatus.FINISHED);
		}

	}

	/**
	 * Starts the next process of the given ExecutionStep.
	 * If the execution is not started, the first step will be started.
	 * If a process requires a hold-out split but none exists, the step will be skipped regardless of the configuration.
	 * If the last step is finished, the execution will be finished.
	 *
	 * @param executionStep The execution step.
	 * @throws BadStateException                   If no original data set exist.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported.
	 * @throws InternalInvalidStateException       If no ExternalProcessEntity exists for the given step.
	 *                                             If a finished process does not contain data set.
	 * @throws InternalIOException                 If the request could not be created.
	 * @throws InternalMissingHandlingException    If no implementation exists for a valid configuration.
	 * @throws InternalRequestException            If the request to the external server for starting the process failed.
	 */
	private void startNext(final ExecutionStepEntity executionStep)
			throws BadStateException, InternalDataSetPersistenceException, InternalInvalidStateException, InternalIOException, InternalMissingHandlingException, InternalRequestException {
		// Get the next step
		Integer nextJob = null;
		ExternalProcessEntity nextProcess = null;

		Integer lastJob = executionStep.getCurrentProcessIndex();
		int jobCandidate;
		ExternalProcessEntity processCandidate;
		boolean foundNext = false;

		while (!foundNext) {
			if (lastJob == null) {
				jobCandidate = 0;
			} else {
				final var lastStepStatus = executionStep.getProcess(lastJob).getExternalProcessStatus();
				if (!(lastStepStatus == ProcessStatus.FINISHED || lastStepStatus == ProcessStatus.SKIPPED)) {
					throw new InternalInvalidStateException(InternalInvalidStateException.LAST_STEP_NOT_FINISHED,
					                                        "Cannot start a process if the previous process is not finished or skipped!");
				}

				if (lastJob < executionStep.getProcesses().size() - 1) {
					// Start the next process
					jobCandidate = lastJob + 1;
				} else {
					break;
				}
			}

			executionStep.setCurrentProcessIndex(jobCandidate);
			processCandidate = executionStep.getProcess(jobCandidate);

			// Check if the process should be skipped
			if (processCandidate.isSkip()) {
				processCandidate.setExternalProcessStatus(ProcessStatus.SKIPPED);
				lastJob = jobCandidate;
			} else {
				// Check if a hold-out split is required and present.
				final boolean requiresHoldOut = stepService.requiresHoldOutSplit(processCandidate.getJob());
				final boolean hasHoldOut = processCandidate.getProject().getOriginalData().isHasHoldOut();

				if (requiresHoldOut && !hasHoldOut) {
					processCandidate.setExternalProcessStatus(ProcessStatus.SKIPPED);
					processCandidate.setStatus("The process requires a hold out split, but no hold out split is present!");
					lastJob = jobCandidate;
				} else {
					foundNext = true;
					nextJob = jobCandidate;
					nextProcess = processCandidate;
				}
			}
		}

		// Update the execution
		executionStep.setCurrentProcessIndex(nextJob);

		if (nextJob != null) {
			startOrScheduleProcess(nextProcess);
		} else {
			executionStep.setStatus(ProcessStatus.FINISHED);
		}
	}

	/**
	 * Fetches the staus of the given process from the external server.
	 *
	 * @param externalProcess The process.
	 * @throws InternalInvalidStateException If the process has no server instance.
	 * @throws InternalRequestException If the request failed.
	 */
	private void fetchStatus(final ExternalProcessEntity externalProcess) throws InternalRequestException, InternalInvalidStateException {
		// Get configuration
		final Job stepConfiguration = externalProcess.getJob();
		final ExternalEndpoint ese = stepService.getExternalServerEndpointConfiguration(stepConfiguration);
		final ExternalServerInstance esi = stepService.getExternalServerInstanceConfiguration(externalProcess.getServerInstance());

		final String serverUrl = esi.getUrl();
		final String statusEndpoint = ese.getStatusEndpoint();

		if (statusEndpoint.isEmpty()) {
			return;
		}

		final String url =
				serverUrl + statusEndpoint.replace(PROCESS_ID_PLACEHOLDER, externalProcess.getUuid().toString());

		// Do the request
		try {
			final WebClient webClient = WebClient.builder().baseUrl(serverUrl).build();
			final var response = webClient.get()
			                              .uri(url)
			                              .retrieve()
			                              .onStatus(HttpStatusCode::isError,
			                                        errorResponse -> errorResponse.toEntity(String.class)
			                                                                      .map(httpService::buildErrorResponse))
			                              .bodyToMono(String.class)
			                              .block();
			externalProcess.setStatus(response);
		} catch (final RequestRuntimeException e) {
			final String message = httpService.buildError(e, "fetch the status");
			throw new InternalRequestException(InternalRequestException.PROCESS_STATUS, message);
		} catch (WebClientRequestException e) {
			var message = "Failed to fetch the status! " + e.getMessage();
			throw new InternalRequestException(InternalRequestException.PROCESS_STATUS, message);
		}
	}

	/**
	 * Schedules the given process by setting the status to {@link ProcessStatus#SCHEDULED}.
	 *
	 * @param externalProcess The process to be scheduled.
	 */
	private void scheduleProcess(final BackgroundProcessEntity externalProcess) {
		externalProcess.setScheduledTime(Timestamp.valueOf(LocalDateTime.now()));
		externalProcess.setExternalProcessStatus(ProcessStatus.SCHEDULED);
	}

	/**
	 * Starts the next scheduled process for the given endpoint using the given instance.
	 *
	 * @param endpoint The endpoint.
	 * @param instance The instance to be used.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected void startScheduledProcess(final ExternalEndpoint endpoint, final ExternalServerInstance instance) {
		final var server = endpoint.getServer();

		final Set<Integer> endpoints = server.getEndpoints().stream()
		                                     .map(ExternalEndpoint::getIndex)
		                                     .collect(Collectors.toSet());
		// TODO account for endpoint max processes
		final List<BackgroundProcessEntity> processes = backgroundProcessRepository.findByEndpointInAndExternalProcessStatusOrderByScheduledTimeAsc(
				endpoints, ProcessStatus.SCHEDULED);

		if (processes.isEmpty()) {
			return;
		}

		for (final var externalProcess : processes) {
			try {
				doStartBackgroundProcess(externalProcess, instance);
				externalProcess.setScheduledTime(null);
				projectRepository.save(externalProcess.getProject());
				break;
			} catch (final ApiException e) {
				log.warn("Failed to start scheduled process!", e);
				setProcessError(externalProcess, e.getMessage());
				projectRepository.save(externalProcess.getProject());
			}
		}
	}

	/**
	 * Starts the given process with the given data.
	 *
	 * @param externalProcess The process to be started.
	 * @param instance        The instance to be used.
	 * @throws BadStateException                   If no original data set exist.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 * @throws InternalInvalidStateException       If a finished process does not contain a dataset.
	 * @throws InternalIOException                 If the request body could not be created.
	 * @throws InternalMissingHandlingException    If no implementation exists for a valid configuration.
	 * @throws InternalRequestException            If the request starting the process failed.
	 */
	private void doStartBackgroundProcess(final BackgroundProcessEntity externalProcess, final ExternalServerInstance instance)
			throws InternalDataSetPersistenceException, InternalIOException, InternalRequestException, BadStateException, InternalInvalidStateException, InternalMissingHandlingException {
		final var endpoint = cinnamonConfiguration.getExternalServerEndpoints().get(externalProcess.getEndpoint());

		// Prepare body
		final MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

		// Add configured input data sets
		addDataSets(externalProcess, endpoint, bodyBuilder);

		// Add config
		final String configuration = externalProcess.getConfigurationString();
		if (configuration != null) {
			addConfig(configuration, endpoint, bodyBuilder);
		}

		// Generate new UUID
		final UUID uuid = UUID.randomUUID();
		externalProcess.setUuid(uuid);

		bodyBuilder.part("session_key", uuid.toString());
		final String callbackHost = instance.getCallbackHost();
		final var serverAddress = ServletUriComponentsBuilder.fromCurrentContextPath()
		                                                     .host(callbackHost)
		                                                     .port(this.port)
		                                                     .build()
		                                                     .toUriString();

		bodyBuilder.part(endpoint.getCallbackPartName(),
		                 serverAddress + "/api/process/" + uuid.toString() + "/callback");

		// Do the request
		try {
			final String serverUrl = instance.getUrl();
			String url = endpoint.getProcessEndpoint().isBlank() ? externalProcess.getConfiguration().getProcessUrl()
			                                                     : endpoint.getProcessEndpoint();
			url = injectUrlParameter(url, externalProcess);

			HttpClient client = HttpClient.create()
			                              .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
			                              .responseTimeout(Duration.ofSeconds(10));
			final WebClient webClient = WebClient.builder()
			                                     .clientConnector(new ReactorClientHttpConnector(client))
			                                     .baseUrl(serverUrl)
			                                     .build();
			final var response = webClient.post()
			                              .uri(url)
			                              .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
			                              .retrieve()
			                              .onStatus(HttpStatusCode::isError,
			                                        errorResponse -> errorResponse.toEntity(String.class)
			                                                                      .map(httpService::buildErrorResponse))
			                              .bodyToMono(ExternalProcessResponse.class)
			                              .block();

			if (response == null) {
				throw new InternalRequestException(InternalRequestException.PROCESS_START, "Failed to read response!");
			}

			externalProcess.setExternalId(response.getPid());
			externalProcess.setExternalProcessStatus(ProcessStatus.RUNNING);
			externalProcess.setServerInstance(instance.getId());
		} catch (final RequestRuntimeException e) {
			final String message = httpService.buildError(e, "start the process");
			throw new InternalRequestException(InternalRequestException.PROCESS_START, message);
		} catch (WebClientRequestException e) {
			var message = "Failed to start the process! ";
			if (e.getCause() instanceof ConnectTimeoutException || e.getCause() instanceof ReadTimeoutException) {
				message += "Connection timed out!";
			} else if (e.getMessage() != null) {
				message += e.getMessage();
			}
			throw new InternalRequestException(InternalRequestException.PROCESS_START, message);
		}
	}

	private void addConfig(final String configuration, final ExternalEndpoint stepConfiguration,
	                       final MultipartBodyBuilder bodyBuilder)
			throws InternalIOException, InternalMissingHandlingException {
		switch (stepConfiguration.getConfigurationEncoding()) {
			case FILE -> {
				bodyBuilder.part(stepConfiguration.getConfigurationPartName(),
				                 new ByteArrayResource(configuration.getBytes()) {
					                 @Override
					                 public String getFilename() {
						                 return "synthesizer_config.yaml";
					                 }
				                 });
			}
			case JSON -> {
				//Convert yaml config to json for anonymization controller
				try {
					final String jsonConfig = YamlMapper.toJson(configuration);
					bodyBuilder.part(stepConfiguration.getConfigurationPartName(), jsonConfig,
					                 MediaType.APPLICATION_JSON);
				} catch (JsonProcessingException e) {
					throw new InternalIOException(InternalIOException.CONFIGURATION_SERIALIZATION,
					                              "Could not convert configuration from yaml to json!", e);
				}
			}
			default -> {
				throw new InternalMissingHandlingException(
						InternalMissingHandlingException.STEP_INPUT_ENCODING,
						"No handling for adding data set of type '" + stepConfiguration.getConfigurationEncoding() +
						"'!");
			}
		}
	}

	private void addDataSets(final BackgroundProcessEntity externalProcess, final ExternalEndpoint ese,
	                         final MultipartBodyBuilder bodyBuilder)
			throws InternalDataSetPersistenceException, InternalIOException, BadStateException, InternalInvalidStateException, InternalMissingHandlingException {
		for (final StepInputConfiguration inputDataSet : ese.getInputs()) {
			final var datasetEntity = dataSetService.getDataSet(inputDataSet.getSelector(), externalProcess);
			final var holdOut = inputDataSet.getSelector() == DataSetSelector.HOLD_OUT
			                    ? HoldOutSelector.HOLD_OUT
			                    : HoldOutSelector.NOT_HOLD_OUT;
			final var dataset = databaseService.exportDataSet(datasetEntity, holdOut);
			addDataSet(bodyBuilder, inputDataSet, dataset);
		}
	}

	private void addDataSet(final MultipartBodyBuilder bodyBuilder,
	                        final StepInputConfiguration stepInputConfiguration,
	                        final DataSet dataSet)
			throws InternalIOException, InternalMissingHandlingException {
		switch (stepInputConfiguration.getEncoding()) {
			case FILE -> {
				addDataSetFile(bodyBuilder, stepInputConfiguration, dataSet);
			}
			case JSON -> {
				addDataSetJson(bodyBuilder, stepInputConfiguration, dataSet);
			}
			default -> {
				throw new InternalMissingHandlingException(
						InternalMissingHandlingException.STEP_INPUT_ENCODING,
						"No handling for adding data set of type '" + stepInputConfiguration.getEncoding() + "'!");
			}
		}
	}

	public void addDataSetJson(final MultipartBodyBuilder bodyBuilder,
	                           final StepInputConfiguration stepInputConfiguration, final DataSet dataSet)
			throws InternalIOException {
		try {
			final String dataSetString = JsonMapper.jsonMapper().writeValueAsString(dataSet);
			bodyBuilder.part(stepInputConfiguration.getPartName(), new ByteArrayResource(dataSetString.getBytes()) {
				@Override
				public String getFilename() {
					return stepInputConfiguration.getFileName();
				}
			});
		} catch (final JsonProcessingException e) {
			throw new InternalIOException(InternalIOException.DATA_SET_SERIALIZATION,
			                              "Could not convert dataset to json!", e);
		}
	}

	public void addDataSetFile(final MultipartBodyBuilder bodyBuilder,
	                           final StepInputConfiguration stepInputConfiguration, final DataSet dataSet)
			throws InternalIOException, InternalMissingHandlingException {
		final var outputStream = new ByteArrayOutputStream();

		final DataProcessor dataProcessor = dataProcessorService.getDataProcessor(FileType.CSV);
		dataProcessor.write(outputStream, dataSet);

		bodyBuilder.part(stepInputConfiguration.getPartName(), new ByteArrayResource(outputStream.toByteArray()) {
			@Override
			public String getFilename() {
				return stepInputConfiguration.getFileName();
			}
		});

		try {
			bodyBuilder.part(stepInputConfiguration.getDataConfigurationName(), new ByteArrayResource(
					yamlMapper.writeValueAsString(dataSet.getDataConfiguration()).getBytes()) {
				@Override
				public String getFilename() {
					return stepInputConfiguration.getDataConfigurationName() + ".yaml";
				}
			});
		} catch (JsonProcessingException e) {
			throw new InternalIOException(InternalIOException.DATA_CONFIGURATION_SERIALIZATION,
			                              "Failed to create the data configuration!", e);
		}
	}

	public void setProcessError(final BackgroundProcessEntity process, final String message) {
		process.setExternalProcessStatus(ProcessStatus.ERROR);
		process.setServerInstance(null);
		process.setScheduledTime(null);
		process.setUuid(null);

		if (process instanceof ExternalProcessEntity externalProcess) {
			externalProcess.setStatus(message);

			final ExecutionStepEntity executionStep = externalProcess.getExecutionStep();
			executionStep.setStatus(ProcessStatus.ERROR);
			executionStep.setCurrentProcessIndex(null);
		}
	}

	private void setProcessError(final ExecutionStepEntity executionStep, final String message) {
		final var currentProcess = executionStep.getCurrentProcess();
		if (currentProcess != null) {
			currentProcess.setExternalProcessStatus(ProcessStatus.ERROR);
			currentProcess.setUuid(null);
			currentProcess.setServerInstance(null);
			currentProcess.setScheduledTime(null);
			currentProcess.setStatus(message);
		}

		executionStep.setStatus(ProcessStatus.ERROR);
		executionStep.setCurrentProcessIndex(null);
	}

	private String injectUrlParameter(final String url, final BackgroundProcessEntity externalProcess) {
		return url.replace(PROCESS_ID_PLACEHOLDER, externalProcess.getUuid().toString());
	}

	/**
	 * Resets the given stage by deleting all results and resetting the status.
	 *
	 * @param executionStep Stage to delete.
	 * @return The updated execution entity.
	 * @throws BadStateException                   If a process of the stage is running.
	 * @throws InternalDataSetPersistenceException If a dataset table could not be deleted.
	 */
	private ExecutionStepEntity deleteStage(final ExecutionStepEntity executionStep)
			throws BadStateException, InternalDataSetPersistenceException {
		if (executionStep.getStatus() == ProcessStatus.RUNNING ||
		    executionStep.getStatus() == ProcessStatus.SCHEDULED) {
			throw new BadStateException(BadStateException.PROCESS_STARTED,
			                            "Stage cannot be deleted because processes are running");
		}

		for (final ExternalProcessEntity externalProcessEntity : executionStep.getProcesses()) {
			resetProcess(externalProcessEntity);
			externalProcessEntity.setConfiguration(null);
		}

		executionStep.setCurrentProcessIndex(null);
		executionStep.setStatus(ProcessStatus.NOT_STARTED);

		return executionStep;
	}

	/**
	 * Resets the results of the given process.
	 *
	 * @param externalProcess The process to reset.
	 * @throws InternalDataSetPersistenceException If a dataset table could not be deleted.
	 */
	private void resetProcess(final ExternalProcessEntity externalProcess) throws InternalDataSetPersistenceException {
		externalProcess.reset();

		if (externalProcess instanceof DataProcessingEntity dataProcessing) {
			databaseService.deleteDataSet(dataProcessing.getDataSet());
			dataProcessing.setDataSet(null);
		}
	}
}
