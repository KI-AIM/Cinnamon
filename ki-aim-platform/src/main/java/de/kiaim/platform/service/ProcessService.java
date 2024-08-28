package de.kiaim.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.configuration.data.DateFormatConfiguration;
import de.kiaim.model.configuration.data.DateTimeFormatConfiguration;
import de.kiaim.model.data.DataRow;
import de.kiaim.model.data.DataSet;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.platform.config.SerializationConfig;
import de.kiaim.platform.config.StepConfiguration;
import de.kiaim.platform.exception.*;
import de.kiaim.platform.model.dto.SynthetizationResponse;
import de.kiaim.platform.model.entity.ExternalProcessEntity;
import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.repository.ExternalProcessRepository;
import de.kiaim.platform.repository.ProjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.scheduling.annotation.Async;
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
import java.util.*;

/**
 * Service class for managing processes.
 */
@Service
@Slf4j
public class ProcessService {

	private static final String PROCESS_ID_PLACEHOLDER = "PROCESS_ID";

	private final int port;

	private final ObjectMapper yamlMapper;

	private final ExternalProcessRepository externalProcessRepository;
	private final ProjectRepository projectRepository;

	private final DatabaseService databaseService;
	private final StepService stepService;

	public ProcessService(final SerializationConfig serializationConfig,
	                      @Value("${server.port}") final int port,
	                      final ExternalProcessRepository externalProcessRepository,
	                      final ProjectRepository projectRepository,
	                      final DatabaseService databaseService,
	                      final StepService stepService
	) {
		this.yamlMapper = serializationConfig.yamlMapper();

		this.port = port;
		this.externalProcessRepository = externalProcessRepository;
		this.projectRepository = projectRepository;
		this.databaseService = databaseService;
		this.stepService = stepService;
	}

	/**
	 * Returns the status of the process for the given step in the given project.
	 *
	 * @param project  The project.
	 * @param stepName The name of the step.
	 * @return The Status of the process.
	 * @throws BadStepNameException          If the step is not defined or the step does contain an external process.
	 * @throws InternalInvalidStateException If no ExternalProcessEntity exists for the given step.
	 */
	@Transactional
	public ExternalProcessEntity getProcess(final ProjectEntity project, final String stepName)
			throws BadStepNameException, InternalInvalidStateException {
		final Step step = Step.getStepOrThrow(stepName);

		if (!step.isHasExternalProcessing()) {
			final var dummy = new ExternalProcessEntity();
			dummy.setExternalProcessStatus(ProcessStatus.NOT_REQUIRED);
			return dummy;
		}

		if (!project.getProcesses().containsKey(step)) {
			throw new InternalInvalidStateException(InternalInvalidStateException.MISSING_PROCESS_ENTITY,
			                                        "No process entity for step '" + step.name() + "' available!");
		}
		return project.getProcesses().get(step);
	}

	/**
	 * Starts an external process.
	 *
	 * @param project       The project the process corresponds to.
	 * @param stepName      Name of the step in the configuration
	 * @param url           URL to start the process.
	 * @param configuration The process specific configuration.
	 * @throws BadColumnNameException                    If the data set does not contain a column with the given names.
	 * @throws BadDataSetIdException                     If no DataConfiguration is associated with the given project.
	 * @throws BadStepNameException                      If the given step name is not defined in the application properties.
	 * @throws InternalApplicationConfigurationException If the given step is not configured.
	 * @throws InternalDataSetPersistenceException       If the data set could not be exported due to an internal error.
	 * @throws InternalInvalidStateException             If no ExternalProcessEntity exists for the given step.
	 * @throws InternalIOException                       If the request body could not be created.
	 * @throws InternalRequestException                  If the request to start the process failed.
	 */
	@Transactional
	public ExternalProcessEntity startProcess(final ProjectEntity project, final String stepName, final String url,
	                                          final String configuration)
			throws BadColumnNameException, BadDataSetIdException, BadStepNameException, InternalApplicationConfigurationException, InternalDataSetPersistenceException, InternalInvalidStateException, InternalIOException, InternalRequestException {
		// Get Step
		final Step step = Step.getStepOrThrow(stepName);

		// Get process entity
		if (!project.getProcesses().containsKey(step)) {
			throw new InternalInvalidStateException(InternalInvalidStateException.MISSING_PROCESS_ENTITY,
			                                        "No process entity for step '" + step.name() + "' available!");
		}
		final ExternalProcessEntity externalProcess = project.getProcesses().get(step);

		if (externalProcess.getExternalProcessStatus() == ProcessStatus.SCHEDULED ||
		    externalProcess.getExternalProcessStatus() == ProcessStatus.RUNNING) {
			return externalProcess;
		}

		// Get configuration
		final StepConfiguration stepConfiguration = stepService.getStepConfiguration(step);
		final String serverUrl = stepConfiguration.getUrl();
		final String callbackHost = stepConfiguration.getCallbackHost();

		// Prepare body
		final MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

		try {
			final DataSet dataSet = databaseService.exportDataSet(project, new ArrayList<>());

			// TODO put somewhere else
			// Set date format
			if (stepConfiguration.getPreProcessors().contains("dateformat")) {
				for (final var columnConfiguration : dataSet.getDataConfiguration().getConfigurations()) {
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

			final var outputStream = new ByteArrayOutputStream();
			final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
			final CSVFormat csvFormat = CSVFormat.Builder.create().setHeader(
					dataSet.getDataConfiguration().getColumnNames().toArray(new String[0])).build();

			final CSVPrinter csvPrinter = new CSVPrinter(outputStreamWriter, csvFormat);
			for (final DataRow dataRow : dataSet.getDataRows()) {
				csvPrinter.printRecord(dataRow.getRow());
			}
			csvPrinter.flush();

			bodyBuilder.part("data", new ByteArrayResource(outputStream.toByteArray()) {
				@Override
				public String getFilename() {
					return "real_data.csv";
				}
			});
			bodyBuilder.part("attribute_config", new ByteArrayResource(
					yamlMapper.writeValueAsString(dataSet.getDataConfiguration()).getBytes()) {
				@Override
				public String getFilename() {
					return "attribute_config.yaml";
				}
			});
			bodyBuilder.part("algorithm_config", new ByteArrayResource(configuration.getBytes()) {
				@Override
				public String getFilename() {
					return "synthesizer_config.yaml";
				}
			});

		} catch (IOException e) {
			throw new InternalIOException(InternalIOException.ZIP_CREATION,
			                              "Failed to create the ZIP file for starting an external process!", e);
		}

		bodyBuilder.part("session_key", externalProcess.getId().toString());
		final var serverAddress = ServletUriComponentsBuilder.fromCurrentContextPath()
		                                                     .host(callbackHost)
		                                                     .port(this.port)
		                                                     .build()
		                                                     .toUriString();
		bodyBuilder.part("callback",
		                 serverAddress + "/api/process/" + externalProcess.getId().toString() + "/callback");

		// Do the request
		try {
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
				message += " with message: '" + e.getResponse().getBody().getMessage() + "'";
			}
			throw new InternalRequestException(InternalRequestException.PROCESS_START, message);
		}

		// Update status
		projectRepository.save(project);

		return externalProcess;
	}

	@Transactional
	public ExternalProcessEntity getStatus(final ProjectEntity project, final String stepName)
			throws BadStepNameException, InternalInvalidStateException, InternalApplicationConfigurationException, InternalRequestException {
		final ExternalProcessEntity externalProcess = getProcess(project, stepName);

		if (externalProcess.getExternalProcessStatus() == ProcessStatus.RUNNING) {
			fetchStatus(externalProcess);
		}

		return externalProcess;
	}

	/**
	 * Cancels the process for the given step name.
	 *
	 * @param project  The project.
	 * @param stepName The step name of which the process should be canceled.
	 * @return The canceled process object.
	 * @throws BadStepNameException                      If the step name is not defined.
	 * @throws InternalApplicationConfigurationException If the step is not configured.
	 * @throws InternalInvalidStateException             If no ExternalProcessEntity exists for the given step.
	 */
	@Transactional
	public ExternalProcessEntity cancelProcess(final ProjectEntity project, final String stepName)
			throws BadStepNameException, InternalApplicationConfigurationException, InternalInvalidStateException {
		// Get Step
		final Step step = Step.getStepOrThrow(stepName);

		if (!project.getProcesses().containsKey(step)) {
			throw new InternalInvalidStateException(InternalInvalidStateException.MISSING_PROCESS_ENTITY,
			                                        "No process entity for step '" + step.name() + "' available!");
		}
		final ExternalProcessEntity externalProcess = project.getProcesses().get(step);

		if (!(externalProcess.getExternalProcessStatus() == ProcessStatus.SCHEDULED ||
		      externalProcess.getExternalProcessStatus() == ProcessStatus.RUNNING)) {
			return externalProcess;
		}

		// Get configuration
		final StepConfiguration stepConfiguration = stepService.getStepConfiguration(step);
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

		externalProcess.setExternalProcessStatus(ProcessStatus.CANCELED);
		projectRepository.save(project);

		return externalProcess;
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
			throws BadProcessIdException, InternalIOException, InternalRequestException, InternalApplicationConfigurationException {
		final Optional<ExternalProcessEntity> process = externalProcessRepository.findById(processId);

		// Invalid processID
		if (process.isEmpty()) {
			throw new BadProcessIdException(BadProcessIdException.NO_PROCESS,
			                                "No process with the given ID '" + processId +
			                                "' exists!");
		}

		final ProjectEntity project = process.get().getProject();
		final var files = process.get().getAdditionalResultFiles();
		files.clear();

		for (final var entry : resultFiles) {
			try {
				final var value = entry.getValue();
				if (entry.getKey().equals("synthetic_data")) {
					process.get().setResultDataSet(value.getBytes());
				} else {
					files.put(value.getOriginalFilename(), value.getBytes());
				}

			} catch (final IOException e) {
				throw new InternalIOException(InternalIOException.MULTIPART_READING,
				                              "Failed to read result file '" + entry.getKey() + "'!", e);
			}
		}

//		fetchStatus(process.get());
		fetchStatusAsync(process.get());

		// Update status
		process.get().setExternalProcessStatus(ProcessStatus.FINISHED);

		projectRepository.save(project);
	}

	@Async
	protected void fetchStatusAsync(final ExternalProcessEntity externalProcess)
			throws InternalRequestException, InternalApplicationConfigurationException {
		fetchStatus(externalProcess);
	}

	private void fetchStatus(final ExternalProcessEntity externalProcess)
			throws InternalRequestException, InternalApplicationConfigurationException {
		// Get configuration
		final StepConfiguration stepConfiguration = stepService.getStepConfiguration(externalProcess.getStep());
		final String serverUrl = stepConfiguration.getUrl();
		final String statusEndpoint = stepConfiguration.getStatusEndpoint();
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
		projectRepository.save(externalProcess.getProject());
	}
}
