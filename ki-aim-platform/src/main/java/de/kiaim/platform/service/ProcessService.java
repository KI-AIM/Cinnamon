package de.kiaim.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.model.data.DataRow;
import de.kiaim.model.data.DataSet;
import de.kiaim.model.dto.ExternalProcessResponse;
import de.kiaim.model.serialization.mapper.JsonMapper;
import de.kiaim.model.serialization.mapper.YamlMapper;
import de.kiaim.model.status.synthetization.SynthetizationStatus;
import de.kiaim.platform.config.SerializationConfig;
import de.kiaim.platform.model.configuration.StepConfiguration;
import de.kiaim.platform.model.configuration.StepInputConfiguration;
import de.kiaim.platform.exception.*;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.entity.*;
import de.kiaim.platform.model.enumeration.DataSetSelector;
import de.kiaim.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.model.file.CsvFileConfiguration;
import de.kiaim.platform.processor.CsvProcessor;
import de.kiaim.platform.repository.ExternalProcessRepository;
import de.kiaim.platform.repository.ProjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

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

	private final ExternalProcessRepository externalProcessRepository;
	private final ProjectRepository projectRepository;

	private final CsvProcessor csvProcessor;
	private final DatabaseService databaseService;
	private final StepService stepService;

	public ProcessService(final SerializationConfig serializationConfig,
	                      @Value("${server.port}") final int port,
	                      final ExternalProcessRepository externalProcessRepository,
	                      final ProjectRepository projectRepository, CsvProcessor csvProcessor,
	                      final DatabaseService databaseService,
	                      final StepService stepService
	) {
		this.jsonMapper = serializationConfig.jsonMapper();
		this.yamlMapper = serializationConfig.yamlMapper();

		this.port = port;
		this.externalProcessRepository = externalProcessRepository;
		this.projectRepository = projectRepository;
		this.csvProcessor = csvProcessor;
		this.databaseService = databaseService;
		this.stepService = stepService;
	}

	/**
	 * Updates and returns the status of the execution of the given step in the given project.
	 * If a process is running, the status of that process will be fetched from the external server.
	 *
	 * @param project The project.
	 * @param step    The step.
	 * @return The updated execution.
	 * @throws InternalApplicationConfigurationException If the step is not configured.
	 * @throws InternalRequestException                  If the request to the external server failed.
	 */
	@Transactional
	public ExecutionStepEntity getStatus(final ProjectEntity project, final Step step)
			throws InternalRequestException, InternalApplicationConfigurationException {
		final var executionStep = project.getPipelines().get(0).getStageByStep(step);

		if (executionStep.getStatus() == ProcessStatus.RUNNING) {
			updateProcessStatus(executionStep.getCurrentProcess());
		}

		return executionStep;
	}

	/**
	 * Saves the configuration and the URL of the selected algorithm for the process of the given step.
	 *
	 * @param project       The project.
	 * @param execStep      The step.
	 * @param stepName      The name of the corresponding step.
	 * @param url           The URL to start the algorithm.
	 * @param configuration The configuration for the algorithm.
	 * @throws BadStepNameException                      If the step name is not valid.
	 * @throws BadStateException                         If the corresponding process is already running or scheduled.
	 * @throws InternalInvalidStateException             If the process entity is missing.
	 */
	@Transactional
	public void configureProcess(final ProjectEntity project, final Step execStep, final String stepName,
	                             final String url, final String configuration)
			throws BadStepNameException, BadStateException, InternalInvalidStateException {
		final Step step = Step.getStepOrThrow(stepName);

		final var executionStep = project.getPipelines().get(0).getStageByStep(execStep);

		// Get process entity
		final Optional<ExternalProcessEntity> optional = executionStep.getProcess(step);
		if (optional.isEmpty()) {
			throw new InternalInvalidStateException(InternalInvalidStateException.MISSING_PROCESS_ENTITY,
			                                        "No process entity for step '" + step.name() + "' available!");
		}
		final ExternalProcessEntity externalProcess = optional.get();

		databaseService.storeConfiguration(configuration, externalProcess);

		if (externalProcess.getExternalProcessStatus() == ProcessStatus.SCHEDULED ||
		    externalProcess.getExternalProcessStatus() == ProcessStatus.RUNNING) {
			throw new BadStateException(BadStateException.PROCESS_STARTED,
			                            "Process cannot be configured if the it is scheduled or started!");
		}

		externalProcess.setProcessUrl(url);

		// Update status
		projectRepository.save(project);
	}

	/**
	 * Starts the execution of the given step in the given project.
	 *
	 * @param project The project the process corresponds to.
	 * @param step    The step the process corresponds to.
	 * @throws BadDataSetIdException                     If no DataConfiguration is associated with the given project.
	 * @throws BadStateException                         If no original data set exist.
	 * @throws InternalApplicationConfigurationException If the given step is not configured.
	 * @throws InternalDataSetPersistenceException       If the data set could not be exported due to an internal error.
	 * @throws InternalInvalidStateException             If no ExternalProcessEntity exists for the given step.
	 *                                                   If a finished process does not contain data set.
	 * @throws InternalIOException                       If the request body could not be created.
	 * @throws InternalMissingHandlingException          If no implementation exists for a valid configuration.
	 * @throws InternalRequestException                  If the request to start the process failed.
	 */
	@Transactional
	public ExecutionStepEntity start(final ProjectEntity project, final Step step)
			throws BadDataSetIdException, BadStateException, InternalApplicationConfigurationException, InternalDataSetPersistenceException, InternalInvalidStateException, InternalIOException, InternalMissingHandlingException, InternalRequestException {
		final var executionStep = project.getPipelines().get(0).getStageByStep(step);

		if (executionStep.getStatus() == ProcessStatus.RUNNING) {
			return executionStep;
		}

		executionStep.setStatus(ProcessStatus.RUNNING);

		// Start the first step
		startNext(executionStep);

		projectRepository.save(project);

		return executionStep;
	}

	/**
	 * Cancels the execution of the given step in the given project.
	 *
	 * @param project The project.
	 * @param step    The step.
	 * @return The updated execution entity.
	 * @throws InternalApplicationConfigurationException If the step is not configured.
	 */
	@Transactional
	public ExecutionStepEntity cancel(final ProjectEntity project, final Step step)
			throws InternalApplicationConfigurationException {
		final var executionStep = project.getPipelines().get(0).getStageByStep(step);

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
	 * Finishes the process with the given process ID.
	 * Checks if the result files contain an error message with key 'error_message'.
	 * If no error message is present, sets the status to 'finished'
	 * and starts the next process of the execution step as well as the next scheduled process of the same step.
	 * If an error is present, aborts the current execution step and stets the status to 'error'.
	 *
	 * @param processId   The ID of the process to finish.
	 * @param resultFiles All files send in the callback request.
	 * @throws BadDataSetIdException                     If the data set could not be exported.
	 * @throws BadProcessIdException                     If the given process ID is not valid.
	 * @throws BadStateException                         If the file for the dataset has not been stored.
	 * @throws InternalApplicationConfigurationException If the step is not configured.
	 * @throws InternalDataSetPersistenceException       If the data set could not be exported.
	 * @throws InternalInvalidResultException            If the estimation of the configuration produced an invalid configuration.
	 * @throws InternalInvalidStateException             If no ExternalProcessEntity exists for the given step.
	 * @throws InternalIOException                       If a result file could not be read.
	 * @throws InternalRequestException                  If the request to the external server for starting the process failed.
	 */
	@Transactional
	public void finishProcess(final Long processId, final Set<Map.Entry<String, MultipartFile>> resultFiles)
			throws ApiException {
		final Optional<ExternalProcessEntity> processOptional = externalProcessRepository.findById(processId);

		// Invalid processID
		if (processOptional.isEmpty()) {
			throw new BadProcessIdException(BadProcessIdException.NO_PROCESS,
			                                "No process with the given ID '" + processId +
			                                "' exists!");
		}
		final var process = processOptional.get();

		final var executionStep = process.getExecutionStep();
		final ProjectEntity project = executionStep.getPipeline().getProject();

		DataProcessingEntity dataProcessing = null;
		if (process instanceof DataProcessingEntity) {
			dataProcessing = (DataProcessingEntity) process;
		}

		final var files = process.getAdditionalResultFiles();
		files.clear();

		boolean containsError = false;
		String errorMessage = null;

		var input = getDataSet(process);
		List<Step> processed = new ArrayList<>(input.getProcessed());
		processed.add(process.getStep());

		for (final var entry : resultFiles) {
			try {
				final var value = entry.getValue();
				if (entry.getKey().equals("synthetic_data")) {
					final FileConfigurationEntity fileConfigurationEntity = new CsvFileConfigurationEntity(
							new CsvFileConfiguration());

					final DataConfiguration resultDataConfiguration = csvProcessor.estimateDataConfiguration(
							value.getInputStream(), fileConfigurationEntity, DatatypeEstimationAlgorithm.MOST_GENERAL);
					final TransformationResult transformationResult = csvProcessor.read(value.getInputStream(),
					                                                                    fileConfigurationEntity,
					                                                                    resultDataConfiguration);
					try {
						databaseService.storeTransformationResult(transformationResult, dataProcessing, processed);
					} catch (final BadDataConfigurationException e) {
						throw new InternalInvalidResultException(InternalInvalidResultException.INVALID_ESTIMATION,
						                                         "Estimation created an invalid configuration!", e);
					}

				} else if (entry.getKey().equals("anonymized_dataset")) {
					String jsonString = IOUtils.toString(value.getInputStream(), StandardCharsets.UTF_8);
					DataSet dataSet = jsonMapper.readValue(jsonString, DataSet.class);

					TransformationResult transformationResult = new TransformationResult(dataSet, new ArrayList<>());
					databaseService.storeTransformationResult(transformationResult, dataProcessing, processed);
				} else if (entry.getKey().equals("exception_message")) {
					containsError = true;
					errorMessage = new String(value.getBytes());
				} else if (!entry.getKey().equals("error_message")) {
					files.put(value.getOriginalFilename(), value.getBytes());
				}

			} catch (final IOException e) {
				throw new InternalIOException(InternalIOException.MULTIPART_READING,
				                              "Failed to read result file '" + entry.getKey() + "'!", e);
			}
		}

		// Hardcoded fix for synthetization callback status
		if (process.getStep() == Step.SYNTHETIZATION) {
			try {
				updateProcessStatus(process);
				final var synthStatus = jsonMapper.readValue(process.getStatus(), SynthetizationStatus.class);
				for (final var abc : synthStatus.getStatus()) {
					abc.setCompleted("True");
				}
				process.setStatus(jsonMapper.writeValueAsString(synthStatus));
			} catch (JsonProcessingException e) {
				log.warn("Failed to update detailed status!", e);
			}
		}

		if (containsError) {
			setProcessError(process, errorMessage);
		} else {
			process.setExternalProcessStatus(ProcessStatus.FINISHED);
			// Start the next step of this process
			startNext(executionStep);
		}

		// Start the next process of the same step
		startScheduledProcess(process.getStep());

		projectRepository.save(project);
	}

	/**
	 * Returns the process in the given project for the step with the given name.
	 * If the process is running, updates the status by calling the external API.
	 *
	 * @param externalProcess The process which status should be updated.
	 * @throws InternalApplicationConfigurationException If the step is not configured.
	 * @throws InternalRequestException                  If the request for the status fails.
	 */
	private void updateProcessStatus(final ExternalProcessEntity externalProcess)
			throws InternalApplicationConfigurationException, InternalRequestException {
		if (externalProcess.getExternalProcessStatus() == ProcessStatus.RUNNING) {
			fetchStatus(externalProcess);
		}
	}

	/**
	 * Starts the given external process if resources are available.
	 * If no resources are available, the process will be scheduled and started if resources are available.
	 *
	 * @param externalProcess The process to be started.
	 * @throws BadDataSetIdException                     If the data set could not be exported.
	 * @throws BadStateException                         If no original data set exist.
	 * @throws InternalApplicationConfigurationException If the step is not configured.
	 * @throws InternalDataSetPersistenceException       If the data set could not be exported.
	 * @throws InternalInvalidStateException             If no ExternalProcessEntity exists for the given step.
	 *                                                   If a finished process does not contain data set.
	 * @throws InternalIOException                       If the request could not be created.
	 * @throws InternalMissingHandlingException          If no implementation exists for a valid configuration.
	 * @throws InternalRequestException                  If the request to the external server for starting the process failed.
	 */
	private void startOrScheduleProcess(final ExternalProcessEntity externalProcess)
			throws BadDataSetIdException, BadStateException, InternalApplicationConfigurationException, InternalDataSetPersistenceException, InternalInvalidStateException, InternalIOException, InternalMissingHandlingException, InternalRequestException {
		// Get configuration
		final StepConfiguration stepConfiguration = stepService.getStepConfiguration(externalProcess.getStep());

		// Check if max number of processes is reached
		if (externalProcessRepository.countByStepAndExternalProcessStatus(externalProcess.getStep(),
		                                                                  ProcessStatus.RUNNING) <
		    stepConfiguration.getMaxParallelProcess()) {
			doStartProcess(externalProcess);
		} else {
			scheduleProcess(externalProcess);
		}
	}


	/**
	 * Cancels the given process.
	 *
	 * @param externalProcess The process to be canceled.
	 * @throws InternalApplicationConfigurationException If the step is not configured.
	 */
	private void cancelProcess(final ExternalProcessEntity externalProcess)
			throws InternalApplicationConfigurationException {
		if (!(externalProcess.getExternalProcessStatus() == ProcessStatus.SCHEDULED ||
		      externalProcess.getExternalProcessStatus() == ProcessStatus.RUNNING)) {
			return;
		}

		if (externalProcess.getExternalProcessStatus() == ProcessStatus.SCHEDULED) {
			externalProcess.setScheduledTime(null);
		} else {
			// Get configuration
			final StepConfiguration stepConfiguration = stepService.getStepConfiguration(externalProcess.getStep());
			final String serverUrl = stepConfiguration.getUrl();
			final String cancelEndpoint = stepConfiguration.getCancelEndpoint()
			                                               .replace(PROCESS_ID_PLACEHOLDER,
			                                                        externalProcess.getId().toString());

			final MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
			formData.add("session_key", externalProcess.getId().toString());
			formData.add("pid", externalProcess.getExternalId());

			// Do the request
			final WebClient webClient = WebClient.builder().baseUrl(serverUrl).build();
			webClient.post()
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

		externalProcess.setExternalProcessStatus(ProcessStatus.CANCELED);
		startScheduledProcess(externalProcess.getStep());
	}

	/**
	 * Starts the next process of the given ExecutionStep.
	 * If the execution is not started, the first step will be started.
	 * If the last step is finished, the execution will be finished.
	 *
	 * @param executionStep The execution step.
	 * @throws BadDataSetIdException                     If the data set could not be exported.
	 * @throws BadStateException                         If no original data set exist.
	 * @throws InternalApplicationConfigurationException If the step is not configured.
	 * @throws InternalDataSetPersistenceException       If the data set could not be exported.
	 * @throws InternalInvalidStateException             If no ExternalProcessEntity exists for the given step.
	 *                                                   If a finished process does not contain data set.
	 * @throws InternalIOException                       If the request could not be created.
	 * @throws InternalMissingHandlingException          If no implementation exists for a valid configuration.
	 * @throws InternalRequestException                  If the request to the external server for starting the process failed.
	 */
	private void startNext(final ExecutionStepEntity executionStep)
			throws BadDataSetIdException, BadStateException, InternalApplicationConfigurationException, InternalDataSetPersistenceException, InternalInvalidStateException, InternalIOException, InternalMissingHandlingException, InternalRequestException {
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
			// Reset status from potential previous execution
			processCandidate.setStatus(null);

			// Check if the process should be skipped
			if (processCandidate.shouldBeSkipped()) {
				processCandidate.setExternalProcessStatus(ProcessStatus.SKIPPED);
				lastJob = jobCandidate;
			} else {
				foundNext = true;
				nextJob = jobCandidate;
				nextProcess = processCandidate;
			}
		}

		// Update the execution
		executionStep.setCurrentProcessIndex(nextJob);

		if (nextJob != null) {
			if (nextProcess.getExternalProcessStatus() != ProcessStatus.SCHEDULED &&
			    nextProcess.getExternalProcessStatus() != ProcessStatus.RUNNING) {
				startOrScheduleProcess(nextProcess);
			}
		} else {
			executionStep.setStatus(ProcessStatus.FINISHED);
		}
	}

	/**
	 * Fetches the staus of the given process from the external server.
	 *
	 * @param externalProcess The process.
	 * @throws InternalApplicationConfigurationException If the step is not configured.
	 * @throws InternalRequestException                  If the request failed.
	 */
	private void fetchStatus(final ExternalProcessEntity externalProcess)
			throws InternalApplicationConfigurationException, InternalRequestException {
		// Get configuration
		final StepConfiguration stepConfiguration = stepService.getStepConfiguration(externalProcess.getStep());
		final String serverUrl = stepConfiguration.getUrl();
		final String statusEndpoint = stepConfiguration.getStatusEndpoint();

		if (statusEndpoint.isEmpty()) {
			return;
		}

		final String url =
				serverUrl + statusEndpoint.replace(PROCESS_ID_PLACEHOLDER, externalProcess.getId().toString());

		// Do the request
		try {
			final WebClient webClient = WebClient.builder().baseUrl(serverUrl).build();
			final var response = webClient.get()
			                              .uri(url)
			                              .retrieve()
			                              .onStatus(HttpStatusCode::isError,
			                                        errorResponse -> errorResponse.toEntity(
					                                                                      ExternalProcessResponse.class)
			                                                                      .map(RequestRuntimeException::new))
			                              .bodyToMono(String.class)
			                              .block();
			externalProcess.setStatus(response);
		} catch (RequestRuntimeException e) {
			var message = "Failed to fetch the status! Got status of " + e.getResponse().getStatusCode();
			if (e.getResponse().getBody() != null) {
				message += " with message: '" + e.getResponse().getBody().getMessage() + "'";
			}
			setProcessError(externalProcess, message);
			throw new InternalRequestException(InternalRequestException.PROCESS_STATUS, message);
		} catch (WebClientRequestException e) {
			var message = "Failed to fetch the status! " + e.getMessage();
			setProcessError(externalProcess, message);
			throw new InternalRequestException(InternalRequestException.PROCESS_STATUS, message);
		}

		// Update status
		projectRepository.save(externalProcess.getExecutionStep().getPipeline().getProject());
	}

	/**
	 * Schedules the given process by setting the status to {@link ProcessStatus#SCHEDULED}.
	 *
	 * @param externalProcess The process to be scheduled.
	 */
	private void scheduleProcess(final ExternalProcessEntity externalProcess) {
		externalProcess.setScheduledTime(Timestamp.valueOf(LocalDateTime.now()));
		externalProcess.setExternalProcessStatus(ProcessStatus.SCHEDULED);
	}

	/**
	 * Starts a scheduled process for the given step.
	 *
	 * @param step The step.
	 */
	private void startScheduledProcess(final Step step) {
		final var process = externalProcessRepository.findFirstByStepAndExternalProcessStatusOrderByScheduledTimeAsc(
				step,
				ProcessStatus.SCHEDULED);

		if (process.isEmpty()) {
			return;
		}
		final var externalProcess = process.get();

		try {
			doStartProcess(externalProcess);
		} catch (final ApiException e) {
			log.warn("Failed to start scheduled process!", e);
			externalProcess.setExternalProcessStatus(ProcessStatus.ERROR);
		}

		externalProcess.setScheduledTime(null);
	}

	/**
	 * Starts the given process by sending a request to the external server.
	 *
	 * @param externalProcess The process to be started.
	 * @throws BadDataSetIdException                     If no DataConfiguration is associated with the given project.
	 * @throws BadStateException                         If no original data set exist.
	 * @throws InternalApplicationConfigurationException If the given step is not configured.
	 * @throws InternalDataSetPersistenceException       If the data set could not be exported due to an internal error.
	 * @throws InternalInvalidStateException             If no ExternalProcessEntity exists for the given step.
	 *                                                   If a finished process does not contain data set.
	 * @throws InternalIOException                       If the request body could not be created.
	 * @throws InternalMissingHandlingException          If no implementation exists for a valid configuration.
	 * @throws InternalRequestException                  If the request to start the process failed.
	 */
	private void doStartProcess(final ExternalProcessEntity externalProcess)
			throws InternalApplicationConfigurationException, InternalDataSetPersistenceException, InternalRequestException, InternalIOException, BadDataSetIdException, InternalInvalidStateException, BadStateException, InternalMissingHandlingException {
		final Step step = externalProcess.getStep();
		final StepConfiguration stepConfiguration = stepService.getStepConfiguration(step);
		final String configuration = externalProcess.getConfiguration();

		if (configuration == null) {
			throw new InternalInvalidStateException(InternalInvalidStateException.MISSING_CONFIGURATION,
			                                        "No configuration for step '" + step.name() + "' found!");
		}

		doStartProcess(stepConfiguration, externalProcess, configuration, externalProcess.getProcessUrl());
	}

	/**
	 * Starts the given process with the given data.
	 *
	 * @param stepConfiguration The configuration of the step.
	 * @param externalProcess   The process to be started.
	 * @param configuration     The configuration string for the process.
	 * @param url               The request URL for starting the process
	 * @throws BadStateException                   If no original data set exist.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 * @throws InternalInvalidStateException       If a finished process does not contain data set.
	 * @throws InternalIOException                 If the request body could not be created.
	 * @throws InternalMissingHandlingException    If no implementation exists for a valid configuration.
	 * @throws InternalRequestException            If the request to start the process failed.
	 */
	private void doStartProcess(final StepConfiguration stepConfiguration,
	                            final ExternalProcessEntity externalProcess, final String configuration,
	                            final String url)
			throws InternalDataSetPersistenceException, InternalIOException, InternalRequestException, BadStateException, InternalInvalidStateException, InternalMissingHandlingException {
		// Prepare body
		final MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

		// Add configured input data sets
		addDataSets(externalProcess, stepConfiguration, bodyBuilder);

		// Add config
		addConfig(configuration, stepConfiguration, bodyBuilder);

		bodyBuilder.part("session_key", externalProcess.getId().toString());
		final String callbackHost = stepConfiguration.getCallbackHost();
		final var serverAddress = ServletUriComponentsBuilder.fromCurrentContextPath()
		                                                     .host(callbackHost)
		                                                     .port(this.port)
		                                                     .build()
		                                                     .toUriString();

		bodyBuilder.part("callback",
		                 serverAddress + "/api/process/" + externalProcess.getId().toString() + "/callback");

		// Do the request
		try {
			final String serverUrl = stepConfiguration.getUrl();
			final WebClient webClient = WebClient.builder().baseUrl(serverUrl).build();
			final var response = webClient.post()
			                              .uri(url)
			                              .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
			                              .retrieve()
			                              .onStatus(HttpStatusCode::isError,
			                                        errorResponse -> errorResponse.toEntity(
					                                                                      ExternalProcessResponse.class)
			                                                                      .map(RequestRuntimeException::new))
			                              .bodyToMono(ExternalProcessResponse.class)
			                              .block();
			externalProcess.setExternalId(response.getPid());
			externalProcess.setExternalProcessStatus(ProcessStatus.RUNNING);
		} catch (RequestRuntimeException e) {
			var message = "Failed to start the process! Got status of " + e.getResponse().getStatusCode();
			if (e.getResponse().getBody() != null) {
				message += " with message: '" + e.getResponse().getBody().getMessage() + "' and error: '" +
				           e.getResponse().getBody().getError() + "'";
			}
			setProcessError(externalProcess, message);
			throw new InternalRequestException(InternalRequestException.PROCESS_START, message);
		} catch (WebClientRequestException e) {
			final var message = "Failed to start the process! " + e.getMessage();
			setProcessError(externalProcess, message);
			throw new InternalRequestException(InternalRequestException.PROCESS_START, message);
		}
	}

	private void addConfig(final String configuration, final StepConfiguration stepConfiguration,
	                       final MultipartBodyBuilder bodyBuilder)
			throws InternalIOException, InternalMissingHandlingException {
		switch (stepConfiguration.getConfigurationEncoding()) {
			case FILE -> {
				bodyBuilder.part(stepConfiguration.getConfigurationPartName(), new ByteArrayResource(configuration.getBytes()) {
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
					bodyBuilder.part(stepConfiguration.getConfigurationPartName(), jsonConfig, MediaType.APPLICATION_JSON);
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

	private void addDataSets(final ExternalProcessEntity externalProcess, final StepConfiguration stepConfiguration,
	                         final MultipartBodyBuilder bodyBuilder)
			throws InternalDataSetPersistenceException, InternalIOException, BadStateException, InternalInvalidStateException, InternalMissingHandlingException {
		for (final StepInputConfiguration inputDataSet : stepConfiguration.getInputs()) {
			final var datasetEntity = getDataSet(inputDataSet.getSelector(), externalProcess);
			final var dataset = databaseService.exportDataSet(datasetEntity);
			addDataSet(bodyBuilder, inputDataSet, dataset);
		}
	}

	public DataSetEntity getDataSet(final ExternalProcessEntity externalProcess)
			throws InternalApplicationConfigurationException, BadStateException, InternalInvalidStateException, InternalMissingHandlingException {
		final StepConfiguration stepConfiguration = stepService.getStepConfiguration(externalProcess.getStep());
		// TODO Hard coded first data set
		return getDataSet(stepConfiguration.getInputs().get(stepConfiguration.getInputs().size() - 1).getSelector(),
		                  externalProcess);
	}

	private DataSetEntity getDataSet(final DataSetSelector dataSetSelector, final ExternalProcessEntity externalProcess)
			throws BadStateException, InternalInvalidStateException, InternalMissingHandlingException {
		final DataSetEntity result;

		switch (dataSetSelector) {
			case LAST_OR_ORIGINAL -> {
				result = getLastOrOriginalDataSet(externalProcess);
			}
			case ORIGINAL -> {
				result = externalProcess.getProject().getOriginalData().getDataSet();
			}
			default -> {
				throw new InternalMissingHandlingException(
						InternalMissingHandlingException.DATA_SET_SELECTOR,
						"No handling for selecting data set '" + dataSetSelector + "'!");
			}
		}

		return result;
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
			bodyBuilder.part(stepInputConfiguration.getPartName(), dataSetString, MediaType.APPLICATION_JSON);
		} catch (final JsonProcessingException e) {
			throw new InternalIOException(InternalIOException.DATA_SET_SERIALIZATION,
			                              "Could not convert dataset to json!", e);
		}
	}

	public void addDataSetFile(final MultipartBodyBuilder bodyBuilder,
	                           final StepInputConfiguration stepInputConfiguration, final DataSet dataSet)
			throws InternalIOException {

		final var outputStream = new ByteArrayOutputStream();
		final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
		final CSVFormat csvFormat = CSVFormat.Builder.create().setHeader(
				dataSet.getDataConfiguration().getColumnNames().toArray(new String[0])).build();

		try {
			final CSVPrinter csvPrinter = new CSVPrinter(outputStreamWriter, csvFormat);
			for (final DataRow dataRow : dataSet.getDataRows()) {
				csvPrinter.printRecord(dataRow.getRow());
			}
			csvPrinter.flush();
		} catch (IOException e) {
			throw new InternalIOException(InternalIOException.CSV_CREATION, "Failed to create the CVS file!", e);
		}

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

	/**
	 * Returns the data set of the last finished step of the given execution.
	 * If no step is finished, returns the original data set.
	 *
	 * @param externalProcess The external process.
	 * @return The data set.
	 * @throws BadStateException             If no original data set exist.
	 * @throws InternalInvalidStateException If a finished process does not contain data set.
	 */
	private DataSetEntity getLastOrOriginalDataSet(final ExternalProcessEntity externalProcess)
			throws BadStateException, InternalInvalidStateException {
		final ExecutionStepEntity executionStep = externalProcess.getExecutionStep();
		final var index = executionStep.getStageIndex();
		final var pipeline = executionStep.getPipeline();

		DataSetEntity result = null;

		for (int i = index; i >= 0; i--) {
			result = getLastDataSet(pipeline.getStages().get(i));
			if (result != null) {
				break;
			}

		}

		if (result == null) {
			result = pipeline.getProject().getOriginalData().getDataSet();
		}

		if (result == null) {
			throw new BadStateException(BadStateException.NO_DATA_SET,
			                            "The project '" + executionStep.getProject().getId() +
			                            "' does not contain an original data set!");
		}

		return result;
	}

	@Nullable
	private DataSetEntity getLastDataSet(final ExecutionStepEntity executionStep) throws InternalInvalidStateException {

		var indexOfSourceStep = executionStep.getCurrentProcessIndex() != null
		                        ? executionStep.getCurrentProcessIndex()
		                        : executionStep.getProcesses().size() - 1;

		DataSetEntity lastOrOriginalDataSet = null;
		while (lastOrOriginalDataSet == null) {

			if (indexOfSourceStep < 0) {
				break;
			} else {
				var candidate = executionStep.getProcess(indexOfSourceStep);
				if (candidate.getExternalProcessStatus() == ProcessStatus.FINISHED &&
				    candidate instanceof DataProcessingEntity dataProcessingEntity) {
					lastOrOriginalDataSet = dataProcessingEntity.getDataSet();

					if (lastOrOriginalDataSet == null) {
						throw new InternalInvalidStateException(InternalInvalidStateException.MISSING_DATA_STET,
						                                        "The job for step " + dataProcessingEntity.getStep() +
						                                        " does not contain a data set!");
					}

				} else {
					indexOfSourceStep--;
				}
			}
		}

		return lastOrOriginalDataSet;
	}

	private void setProcessError(final ExternalProcessEntity process, final String message) {
		process.setExternalProcessStatus(ProcessStatus.ERROR);
		process.setStatus(message);

		final ExecutionStepEntity executionStep = process.getExecutionStep();
		executionStep.setStatus(ProcessStatus.ERROR);
		executionStep.setCurrentProcessIndex(null);
	}

}
