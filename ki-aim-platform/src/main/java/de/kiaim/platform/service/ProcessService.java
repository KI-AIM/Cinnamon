package de.kiaim.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.model.configuration.data.DateFormatConfiguration;
import de.kiaim.model.configuration.data.DateTimeFormatConfiguration;
import de.kiaim.model.data.DataRow;
import de.kiaim.model.data.DataSet;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.model.status.synthetization.SynthetizationStatus;
import de.kiaim.platform.config.SerializationConfig;
import de.kiaim.platform.config.StepConfiguration;
import de.kiaim.platform.exception.*;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.dto.SynthetizationResponse;
import de.kiaim.platform.model.entity.ExecutionStepEntity;
import de.kiaim.platform.model.entity.ExternalProcessEntity;
import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.model.file.CsvFileConfiguration;
import de.kiaim.platform.model.file.FileConfiguration;
import de.kiaim.platform.model.file.FileType;
import de.kiaim.platform.processor.CsvProcessor;
import de.kiaim.platform.repository.ExternalProcessRepository;
import de.kiaim.platform.repository.ProjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
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
	 * @param step The step.
	 * @return The updated execution.
	 * @throws InternalApplicationConfigurationException If the step is not configured.
	 * @throws InternalRequestException                  If the request to the external server failed.
	 */
	@Transactional
	public ExecutionStepEntity getStatus(final ProjectEntity project, final Step step)
			throws InternalRequestException, InternalApplicationConfigurationException {
		final var executionStep = project.getExecutions().get(step);

		if (executionStep.getStatus() == ProcessStatus.RUNNING) {
			updateProcessStatus(executionStep.getProcesses().get(executionStep.getCurrentStep()));
		}

		return executionStep;
	}

	/**
	 * Saves the configuration and the URL of the selected algorithm for the process of the given step.
	 * @param project       The project.
	 * @param execStep      The step.
	 * @param stepName      The name of the corresponding step.
	 * @param url           The URL to start the algorithm.
	 * @param configuration The configuration for the algorithm.
	 * @throws BadStepNameException                      If the step name is not valid.
	 * @throws BadStateException                         If the corresponding process is already running or scheduled.
	 * @throws InternalApplicationConfigurationException If the step has not been configured correctly.
	 * @throws InternalInvalidStateException             If the process entity is missing.
	 */
	@Transactional
	public void configureProcess(final ProjectEntity project, final Step execStep, final String stepName, final String url,
	                             final String configuration)
			throws BadStepNameException, BadStateException, InternalInvalidStateException, InternalApplicationConfigurationException {
		final Step step = Step.getStepOrThrow(stepName);

		final var executionStep = project.getExecutions().get(execStep);

		// Get process entity
		if (!executionStep.getProcesses().containsKey(step)) {
			throw new InternalInvalidStateException(InternalInvalidStateException.MISSING_PROCESS_ENTITY,
			                                        "No process entity for step '" + step.name() + "' available!");
		}
		final ExternalProcessEntity externalProcess = executionStep.getProcesses().get(step);

		databaseService.storeConfiguration(step, configuration, project);

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
	 * @param step The step the process corresponds to.
	 * @throws BadColumnNameException                    If the data set does not contain a column with the given names.
	 * @throws BadDataSetIdException                     If no DataConfiguration is associated with the given project.
	 * @throws InternalApplicationConfigurationException If the given step is not configured.
	 * @throws InternalDataSetPersistenceException       If the data set could not be exported due to an internal error.
	 * @throws InternalInvalidStateException             If no ExternalProcessEntity exists for the given step.
	 * @throws InternalIOException                       If the request body could not be created.
	 * @throws InternalRequestException                  If the request to start the process failed.
	 */
	@Transactional
	public ExecutionStepEntity start(final ProjectEntity project, final Step step)
			throws BadColumnNameException, BadDataSetIdException, InternalApplicationConfigurationException, InternalDataSetPersistenceException, InternalInvalidStateException, InternalIOException, InternalRequestException {
		final var executionStep = project.getExecutions().get(step);

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
	 * @param project The project.
	 * @param step The step.
	 * @return The updated execution entity.
	 * @throws InternalApplicationConfigurationException If the step is not configured.
	 */
	@Transactional
	public ExecutionStepEntity cancel(final ProjectEntity project, final Step step)
			throws InternalApplicationConfigurationException {
		final var executionStep = project.getExecutions().get(step);

		if (executionStep.getStatus() == ProcessStatus.RUNNING) {
			// Get the current step
			final var currentStep = executionStep.getCurrentStep();
			cancelProcess(executionStep.getProcesses().get(currentStep));

			// Update
			executionStep.setStatus(ProcessStatus.CANCELED);
			executionStep.setCurrentStep(null);

			projectRepository.save(project);
		}

		return executionStep;
	}

	/**
	 * Finishes the process with the given process ID.
	 * Sets the status to 'finished' and deletes the ExternalProcess object.
	 *
	 * @param processId The ID of the process to finish.
	 * @throws BadProcessIdException If the given process ID is not valid.
	 */
	@Transactional
	public void finishProcess(final Long processId, final Set<Map.Entry<String, MultipartFile>> resultFiles)
			throws BadProcessIdException, InternalIOException, InternalRequestException, InternalApplicationConfigurationException, InternalInvalidStateException, InternalDataSetPersistenceException, BadColumnNameException, BadDataSetIdException {
		final Optional<ExternalProcessEntity> process = externalProcessRepository.findById(processId);

		// Invalid processID
		if (process.isEmpty()) {
			throw new BadProcessIdException(BadProcessIdException.NO_PROCESS,
			                                "No process with the given ID '" + processId +
			                                "' exists!");
		}

		final var executionStep = process.get().getExecutionStep();
		final ProjectEntity project = executionStep.getProject();


		final var files = process.get().getAdditionalResultFiles();
		files.clear();

		for (final var entry : resultFiles) {
			try {
				final var value = entry.getValue();
				if (entry.getKey().equals("synthetic_data")) {
					final FileConfiguration fileConfiguration = new FileConfiguration();
					fileConfiguration.setFileType(FileType.CSV);
					fileConfiguration.setCsvFileConfiguration(new CsvFileConfiguration());

					final DataConfiguration resultDataConfiguration = csvProcessor.estimateDatatypes(
							value.getInputStream(), fileConfiguration, DatatypeEstimationAlgorithm.MOST_GENERAL);
					final Step step = process.get().getStep();
					final TransformationResult transformationResult = csvProcessor.read(value.getInputStream(),
					                                                                    fileConfiguration,
					                                                                    resultDataConfiguration);
					databaseService.storeTransformationResult(transformationResult, project, step);
				} else {
					files.put(value.getOriginalFilename(), value.getBytes());
				}

			} catch (final IOException e) {
				throw new InternalIOException(InternalIOException.MULTIPART_READING,
				                              "Failed to read result file '" + entry.getKey() + "'!", e);
			}
		}

		process.get().setExternalProcessStatus(ProcessStatus.FINISHED);

		// Hardcoded fix for synthetization callback status
		if (process.get().getStep() == Step.SYNTHETIZATION) {
			try {
				final var synthStatus = jsonMapper.readValue(process.get().getStatus(), SynthetizationStatus.class);
				final var callbackStatus = synthStatus.getStatus()
				                                      .stream()
				                                      .filter(a -> Objects.equals(a.getStep(), "callback"))
				                                      .findFirst();
				if (callbackStatus.isPresent()) {
					callbackStatus.get().setCompleted("True");
					process.get().setStatus(jsonMapper.writeValueAsString(synthStatus));
				}
			} catch (JsonProcessingException e) {
				log.warn("Schade!", e);
			}
		}

		// Start the next process of the same step
		startScheduledProcess(process.get().getStep());

		// Start the next step of this process
		startNext(executionStep);

		projectRepository.save(project);
	}

	/**
	 * Returns the process in the given project for the step with the given name.
	 * If the process is running, updates the status by calling the external API.
	 *
	 * @param externalProcess The process which status should be updated.
	 * @throws InternalApplicationConfigurationException If the step is not configured.
	 * @throws InternalRequestException If the request for the status fails.
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
	 * @throws BadColumnNameException                    If the data set could not be exported.
	 * @throws BadDataSetIdException                     If the data set could not be exported.
	 * @throws InternalApplicationConfigurationException If the step is not configured.
	 * @throws InternalDataSetPersistenceException       If the data set could not be exported.
	 * @throws InternalInvalidStateException             If no ExternalProcessEntity exists for the given step.
	 * @throws InternalRequestException                  If the request to the external server for starting the process failed.
	 * @throws InternalIOException                       If the request could not be created.
	 */
	private void startOrScheduleProcess(final ExternalProcessEntity externalProcess)
			throws BadColumnNameException, InternalApplicationConfigurationException, InternalDataSetPersistenceException, InternalRequestException, InternalIOException, BadDataSetIdException, InternalInvalidStateException {
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
	 * @throws BadColumnNameException                    If the data set could not be exported.
	 * @throws BadDataSetIdException                     If the data set could not be exported.
	 * @throws InternalApplicationConfigurationException If the step is not configured.
	 * @throws InternalDataSetPersistenceException       If the data set could not be exported.
	 * @throws InternalInvalidStateException             If no ExternalProcessEntity exists for the given step.
	 * @throws InternalRequestException                  If the request to the external server for starting the process failed.
	 * @throws InternalIOException                       If the request could not be created.
	 */
	private void startNext(final ExecutionStepEntity executionStep)
			throws BadColumnNameException, BadDataSetIdException, InternalInvalidStateException, InternalDataSetPersistenceException, InternalRequestException, InternalApplicationConfigurationException, InternalIOException {
		// Get the next step
		Step nextStep = null;
		ExternalProcessEntity nextProcess = null;

		Step lastStep = executionStep.getCurrentStep();
		Step stepCandidate;
		ExternalProcessEntity processCandidate;
		boolean foundNext = false;

		while (!foundNext) {
			if (lastStep == null) {
				stepCandidate = executionStep.getStep().getProcesses().get(0);
			} else {
				final var lastStepStatus = executionStep.getProcesses().get(lastStep).getExternalProcessStatus();
				if (!(lastStepStatus == ProcessStatus.FINISHED || lastStepStatus == ProcessStatus.SKIPPED)) {
					throw new InternalInvalidStateException(InternalInvalidStateException.LAST_STEP_NOT_FINISHED,
					                                        "Cannot start a process if the previous process is not finished or skipped!");
				}

				final var stepIndex = executionStep.getStep().getProcesses().indexOf(lastStep);
				if (stepIndex < executionStep.getStep().getProcesses().size() - 1) {
					// Start the next process
					stepCandidate = executionStep.getStep().getProcesses().get(stepIndex + 1);
				} else {
					break;
				}
			}

			if (!executionStep.getProcesses().containsKey(stepCandidate)) {
				throw new InternalInvalidStateException(InternalInvalidStateException.MISSING_PROCESS_ENTITY,
				                                        "No process entity for stepCandidate '" + stepCandidate.name() + "' available!");
			}

			executionStep.setCurrentStep(stepCandidate);

			processCandidate = executionStep.getProcesses().get(stepCandidate);

			// Check if the process should be skipped
			if (Objects.equals(processCandidate.getProcessUrl(), "skip")) {
				processCandidate.setExternalProcessStatus(ProcessStatus.SKIPPED);
				lastStep = stepCandidate;
			} else {
				foundNext = true;
				nextStep = stepCandidate;
				nextProcess = processCandidate;
			}
		}

		// Update the execution
		executionStep.setCurrentStep(nextStep);

		if (nextStep != null) {
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
	 * @throws InternalRequestException If the request failed.
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

		final String url = serverUrl + statusEndpoint.replace(PROCESS_ID_PLACEHOLDER, externalProcess.getId().toString());

		// Do the request
		try {
			final WebClient webClient = WebClient.builder().baseUrl(serverUrl).build();
			final var response = webClient.get()
			                              .uri(url)
			                              .retrieve()
			                              .onStatus(HttpStatusCode::isError,
			                                        errorResponse -> errorResponse.toEntity(
					                                                                      SynthetizationResponse.class)
			                                                                      .map(RequestRuntimeException::new))
			                              .bodyToMono(String.class)
			                              .block();
			externalProcess.setStatus(response);
		} catch (RequestRuntimeException e) {
			var message = "Failed to fetch the status! Got status of " + e.getResponse().getStatusCode();
			if (e.getResponse().getBody() != null) {
				message += " with message: '" + e.getResponse().getBody().getMessage() + "'";
			}
			throw new InternalRequestException(InternalRequestException.PROCESS_STATUS, message);
		}

		// Update status
		projectRepository.save(externalProcess.getExecutionStep().getProject());
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
	 * @throws BadColumnNameException                    If the data set does not contain a column with the given names.
	 * @throws BadDataSetIdException                     If no DataConfiguration is associated with the given project.
	 * @throws InternalApplicationConfigurationException If the given step is not configured.
	 * @throws InternalDataSetPersistenceException       If the data set could not be exported due to an internal error.
	 * @throws InternalInvalidStateException             If no ExternalProcessEntity exists for the given step.
	 * @throws InternalIOException                       If the request body could not be created.
	 * @throws InternalRequestException                  If the request to start the process failed.
	 */
	private void doStartProcess(final ExternalProcessEntity externalProcess)
			throws InternalApplicationConfigurationException, InternalDataSetPersistenceException, InternalRequestException, BadColumnNameException, InternalIOException, BadDataSetIdException, InternalInvalidStateException {
		final Step step = externalProcess.getStep();
		final StepConfiguration stepConfiguration = stepService.getStepConfiguration(step);

		final ProjectEntity project = externalProcess.getExecutionStep().getProject();
		final String configuration = project.getConfigurations().get(stepConfiguration.getConfigurationName());

		if (configuration == null) {
			throw new InternalInvalidStateException(InternalInvalidStateException.MISSING_CONFIGURATION,
			                                        "No configuration with name '" +
			                                        stepConfiguration.getConfigurationName() + "' required for step '" +
			                                        step.name() + "' found!");
		}

		doStartProcess(stepConfiguration, externalProcess, configuration, externalProcess.getProcessUrl());
	}

	/**
	 * Starts the given process with the given data.
	 *
	 * @param externalProcess The process to be started.
	 * @throws BadColumnNameException                    If the data set does not contain a column with the given names.
	 * @throws BadDataSetIdException                     If no DataConfiguration is associated with the given project.
	 * @throws InternalDataSetPersistenceException       If the data set could not be exported due to an internal error.
	 * @throws InternalIOException                       If the request body could not be created.
	 * @throws InternalRequestException                  If the request to start the process failed.
	 */
	private void doStartProcess(final StepConfiguration stepConfiguration,
	                            final ExternalProcessEntity externalProcess, final String configuration,
	                            final String url)
			throws InternalDataSetPersistenceException, BadColumnNameException, InternalIOException, BadDataSetIdException, InternalRequestException, InternalApplicationConfigurationException {
		// Prepare body
		final MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

		addDataSets(externalProcess, stepConfiguration,bodyBuilder);

		final var configName = externalProcess.getStep() == Step.TECHNICAL_EVALUATION ? "evaluation_config" : "algorithm_config";
		if (externalProcess.getStep() == Step.ANONYMIZATION) {
			DataSet dataSet = getLastOrOriginalDataSet(externalProcess.getExecutionStep());
			try {
				String dataSetString = jsonMapper.writeValueAsString(dataSet);
				bodyBuilder.part("data", dataSetString);
			} catch(Exception e) {
				log.error("Could not convert dataset to json: " + e.getMessage());
			}

			bodyBuilder.part("anonymizationConfig", configuration);
		}
		else {
			bodyBuilder.part(configName, new ByteArrayResource(configuration.getBytes()) {
				@Override
				public String getFilename() {
					return "synthesizer_config.yaml";
				}
			});
		}

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
					                                                                      SynthetizationResponse.class)
			                                                                      .map(RequestRuntimeException::new))
			                              .bodyToMono(SynthetizationResponse.class)
			                              .block();
			externalProcess.setExternalId(response.getPid());
			externalProcess.setExternalProcessStatus(ProcessStatus.RUNNING);
		} catch (RequestRuntimeException e) {
			var message = "Failed to start the process! Got status of " + e.getResponse().getStatusCode();
			if (e.getResponse().getBody() != null) {
				message += " with message: '" + e.getResponse().getBody().getMessage() + "' and error: '" + e.getResponse().getBody().getError()  + "'";
			}
			throw new InternalRequestException(InternalRequestException.PROCESS_START, message);
		}
	}

	private void preprocessDataConfiguration(final StepConfiguration stepConfiguration, final DataConfiguration dataConfiguration) {
		if (stepConfiguration.getPreProcessors().contains("dateformat")) {
			for (final var columnConfiguration : dataConfiguration.getConfigurations()) {
				if (columnConfiguration.getType() == DataType.DATE_TIME) {
					boolean found = false;
					for (final var config : columnConfiguration.getConfigurations()) {
						if (config instanceof DateTimeFormatConfiguration dateFormatConfiguration) {
							found = true;
							dateFormatConfiguration.setDateTimeFormatter("%Y-%m-%dT%H:%M:%S.%f");
							break;
						}
					}

					if (!found) {
						columnConfiguration.getConfigurations()
						                   .add(new DateTimeFormatConfiguration("%Y-%m-%dT%H:%M:%S.%f"));
					}

				} else if (columnConfiguration.getType() == DataType.DATE) {

					boolean found = false;
					for (final var config : columnConfiguration.getConfigurations()) {
						if (config instanceof DateFormatConfiguration dateFormatConfiguration) {
							found = true;
							dateFormatConfiguration.setDateFormatter("%Y-%m-%d");
							break;
						}
					}

					if (!found) {
						columnConfiguration.getConfigurations().add(new DateFormatConfiguration("%Y-%m-%d"));
					}
				}
			}
		}
	}

	private void addDataSets(final ExternalProcessEntity externalProcess, final StepConfiguration stepConfiguration,
	                         final MultipartBodyBuilder bodyBuilder)
			throws InternalApplicationConfigurationException, InternalDataSetPersistenceException, BadColumnNameException, InternalIOException, BadDataSetIdException {
		final ProjectEntity project = externalProcess.getExecutionStep().getProject();

		for (final String inputDataSet : stepConfiguration.getInputs()) {
			switch (inputDataSet) {
				case "original": {
					final var dataset = databaseService.exportDataSet(project, new ArrayList<>(), Step.VALIDATION);
					addDataSet(bodyBuilder, stepConfiguration, dataset, "real_data", "real_data.csv", "attribute_config");
					break;
				}
				case "last": {
					final var dataset = getLastOrOriginalDataSet(externalProcess.getExecutionStep());
					addDataSet(bodyBuilder, stepConfiguration, dataset, "data", "real_data.csv", "attribute_config");
					break;
				}
				case "synth": {
					final var synthExecution = project.getExecutions().get(Step.EXECUTION);
					final var dataset = getLastOrOriginalDataSet(synthExecution);
					addDataSet(bodyBuilder, stepConfiguration, dataset, "synthetic_data", "synthetic_data.csv", "attribute_config_synthetic");
					break;
				}
				default: {
					throw new InternalApplicationConfigurationException(
							InternalApplicationConfigurationException.INVALID_INPUT_DATA_SET,
							"Input '" + inputDataSet + "' is not a valid input dateset!");
				}
			}
		}
	}

	private void addDataSet(final MultipartBodyBuilder bodyBuilder, final StepConfiguration stepConfiguration,
	                        final DataSet dataSet, final String partName, final String fileName,
	                        final String dataConfigurationName)
			throws InternalIOException {
		preprocessDataConfiguration(stepConfiguration, dataSet.getDataConfiguration());

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

		bodyBuilder.part(partName, new ByteArrayResource(outputStream.toByteArray()) {
			@Override
			public String getFilename() {
				return fileName;
			}
		});

		try {
			bodyBuilder.part(dataConfigurationName, new ByteArrayResource(
					yamlMapper.writeValueAsString(dataSet.getDataConfiguration()).getBytes()) {
				@Override
				public String getFilename() {
					return dataConfigurationName + ".yaml";
				}
			});
		} catch (JsonProcessingException e) {
			throw new InternalIOException(InternalIOException.DATA_CONFIGURATION_SERIALIZATION,
			                              "Failed to create the data configuration!", e);
		}
	}

	private DataSet getLastOrOriginalDataSet(final ExecutionStepEntity executionStep)
			throws InternalDataSetPersistenceException, BadColumnNameException, InternalIOException, BadDataSetIdException {
		var abc = executionStep.getStep();

		var indexOfSourceStep = executionStep.getCurrentStep() != null
		                        ? abc.getProcesses().indexOf(executionStep.getCurrentStep()) - 1
		                        : abc.getProcesses().size() - 1;

		Step dataSetSourceStep = null;
		while (dataSetSourceStep == null) {

			if (indexOfSourceStep <= 0) {
				dataSetSourceStep = Step.VALIDATION;
			} else {
				var stepCandidate = abc.getProcesses().get(indexOfSourceStep);
				if (executionStep.getProcesses().get(stepCandidate).getExternalProcessStatus() == ProcessStatus.FINISHED) {
					dataSetSourceStep = stepCandidate;
				} else {
					indexOfSourceStep--;
				}
			}
		}

		return databaseService.exportDataSet(executionStep.getProject(), new ArrayList<>(), dataSetSourceStep);
	}

}
