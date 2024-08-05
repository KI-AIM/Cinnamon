package de.kiaim.platform.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.data.DataRow;
import de.kiaim.model.data.DataSet;
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
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
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
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service class for managing processes.
 */
@Service
public class ProcessService {

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
	 * Starts an external process.
	 * @param project The project the process corresponds to.
	 * @param stepName Name of the step in the configuration
	 * @param url URL to start the process.
	 * @param configuration The process specific configuration.
	 * @throws BadColumnNameException If the data set does not contain a column with the given names.
	 * @throws BadDataSetIdException If no DataConfiguration is associated with the given project.
	 * @throws BadStepNameException If the given step name is not defined in the application properties.
	 * @throws InternalApplicationConfigurationException If the given step is not configured.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 * @throws InternalIOException If the request body could not be created.
	 * @throws InternalRequestException If the request to start the process failed.
	 */
	@Transactional
	public ExternalProcessEntity startProcess(final ProjectEntity project, final String stepName, final String url,
	                                          final String configuration)
			throws BadColumnNameException, BadDataSetIdException, BadStepNameException, InternalApplicationConfigurationException, InternalDataSetPersistenceException, InternalIOException, InternalRequestException {
		// Get Step
		final Step step = this.getStep(stepName);

		// Set process entity
		final ExternalProcessEntity externalProcess = project.getProcesses().get(step);

		// Get configuration
		final StepConfiguration stepConfiguration = stepService.getStepConfiguration(step);
		final String serverUrl = stepConfiguration.getUrl();
		final String callbackHost = stepConfiguration.getCallbackHost();

		// Prepare body
		final MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

		try {
			final DataSet dataSet = databaseService.exportDataSet(project, new ArrayList<>());

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
			bodyBuilder.part("attribute_config", new ByteArrayResource(yamlMapper.writeValueAsString(dataSet.getDataConfiguration()).getBytes()) {
				@Override
				public String getFilename() {
					return "attribute_config.yaml";
				}
			});
			bodyBuilder.part("algorithm_config", new ByteArrayResource(configuration.getBytes()) {
				@Override
				public String getFilename() {
					return "algorithm_config.yaml";
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
		AtomicReference<InternalRequestException> apiException = new AtomicReference<>();

		final WebClient webClient = WebClient.builder().baseUrl(serverUrl).build();
		final var response = webClient.post()
		                              .uri(url)
		                              .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
		                              .retrieve()
		                              .onStatus(a -> a.is4xxClientError() || a.is5xxServerError(),
		                                        b -> {
			                                        apiException.set(
					                                        new InternalRequestException(
							                                        InternalRequestException.PROCESS_START,
							                                        "Failed to start the process! Got status of " +
							                                        b.statusCode()));
			                                        return null;
		                                        })
		                              .bodyToMono(SynthetizationResponse.class)
		                              .onErrorComplete()
		                              .block();
		if (apiException.get() != null) {
			throw apiException.get();
		}

		externalProcess.setExternalId(response.getPid());

		// Update status
		externalProcess.setExternalProcessStatus(ProcessStatus.RUNNING);
		projectRepository.save(project);

		return externalProcess;
	}

	public ExternalProcessEntity cancelProcess(final ProjectEntity project, final String stepName)
			throws BadStepNameException, InternalApplicationConfigurationException, InternalRequestException {
		// Get Step
		final Step step = this.getStep(stepName);

		if (!project.getProcesses().containsKey(step)) {
			// TODO throw bad?
		}
		final ExternalProcessEntity externalProcess = project.getProcesses().get(step);

		// Get configuration
		final StepConfiguration stepConfiguration = stepService.getStepConfiguration(step);
		final String serverUrl = stepConfiguration.getUrl();
		final String cancelEndpoint = stepConfiguration.getCancelEndpoint()
		                                               .replace("PROCESS_ID", externalProcess.getId().toString());

		final MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
		formData.add("session_key", externalProcess.getId().toString());
		formData.add("pid", externalProcess.getExternalId());

		// Do the request
		AtomicReference<InternalRequestException> apiException = new AtomicReference<>();
		final WebClient webClient = WebClient.builder().baseUrl(serverUrl).build();
		final var response = webClient.post()
		                              .uri(cancelEndpoint)
		                              .body(BodyInserters.fromFormData(formData))
		                              .retrieve()
		                              .onStatus(a -> a.is4xxClientError() || a.is5xxServerError(),
		                                        b -> {
			                                        apiException.set(
					                                        new InternalRequestException(
							                                        InternalRequestException.PROCESS_CANCEL,
							                                        "Failed to cancel the process! Got status of " +
							                                        b.statusCode()));
			                                        return null;
		                                        })
		                              .bodyToMono(SynthetizationResponse.class)
		                              .onErrorComplete()
		                              .block();
		if (apiException.get() != null) {
			throw apiException.get();
		}

		externalProcess.setExternalProcessStatus(ProcessStatus.CANCELED);
		projectRepository.save(project);

		return externalProcess;
	}

	/**
	 * Finishes the process with the given process ID.
	 * Sets the status to 'finished' and deletes the ExternalProcess object.
	 * @param processId The ID of the process to finish.
	 * @throws BadProcessIdException If the given process ID is not valid.
	 */
	@Transactional
	public void finishProcess(final Long processId, final MultipartFile resultData,
	                          final MultipartFile... additionalFiles)
			throws BadProcessIdException, InternalIOException {
		final Optional<ExternalProcessEntity> process = externalProcessRepository.findById(processId);

		// Invalid processID
		if (process.isEmpty()) {
			throw new BadProcessIdException(BadProcessIdException.NO_PROCESS,
			                                "No process with the given ID '" + processId +
			                                "' exists!");
		}

		final ProjectEntity project = process.get().getProject();
		try {
			process.get().setResultDataSet(resultData.getBytes());

			final var files = process.get().getAdditionalResultFiles();
			files.clear();
			for (final MultipartFile additionalFile : additionalFiles) {
				files.put(additionalFile.getOriginalFilename(), additionalFile.getBytes());
			}
		} catch (final IOException e) {
			throw new InternalIOException(InternalIOException.MULTIPART_READING, "Failed to read result!", e);
		}

		// Update status
		process.get().setExternalProcessStatus(ProcessStatus.FINISHED);
		projectRepository.save(project);
	}

	/**
	 * Writes a ZIP to the given OutputStream containing the data set and data configuration of the given project and the given configuration.
	 * @param project The project of the data set.
	 * @param outputStream The OutputStream to write to.
	 * @throws BadColumnNameException If the data set does not contain a column with the given names.
	 * @throws BadDataSetIdException If no DataConfiguration is associated with the given project.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 * @throws InternalIOException If the request body could not be created.
	 */
	public void createZipFile(final ProjectEntity project, final OutputStream outputStream)
			throws BadColumnNameException, BadDataSetIdException, InternalDataSetPersistenceException, InternalIOException {
		try (final ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
			final DataSet dataSet = databaseService.exportDataSet(project, new ArrayList<>());

			// Add data configuration
			final ZipEntry attributeConfigZipEntry = new ZipEntry("attribute_config.yaml");
			zipOut.putNextEntry(attributeConfigZipEntry);
			yamlMapper.writer().without(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
			          .writeValue(zipOut, dataSet.getDataConfiguration());
			zipOut.closeEntry();

			// Add configuration
			for (final var configurationEntry : project.getConfigurations().entrySet()) {
				final ZipEntry configZipEntry = new ZipEntry(configurationEntry.getKey() + ".yaml");
				zipOut.putNextEntry(configZipEntry);
				zipOut.write(configurationEntry.getValue().getBytes());
				zipOut.closeEntry();
			}

			// Add data set
			final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(zipOut, StandardCharsets.UTF_8);
			final CSVFormat csvFormat = CSVFormat.Builder.create().setHeader(
					dataSet.getDataConfiguration().getColumnNames().toArray(new String[0])).build();

			final ZipEntry dataZipEntry = new ZipEntry("real_data.csv");
			zipOut.putNextEntry(dataZipEntry);

			final CSVPrinter csvPrinter = new CSVPrinter(outputStreamWriter, csvFormat);
			for (final DataRow dataRow : dataSet.getDataRows()) {
				csvPrinter.printRecord(dataRow.getRow());
			}
			csvPrinter.flush();

			zipOut.closeEntry();

			// Add results
			for (final ExternalProcessEntity externalProcess : project.getProcesses().values()) {
				if (externalProcess.getResultDataSet() != null) {
					final ZipEntry resultZipEntry = new ZipEntry(externalProcess.getStep().name() + "-result.csv");
					zipOut.putNextEntry(resultZipEntry);
					zipOut.write(externalProcess.getResultDataSet());
					zipOut.closeEntry();
				}

				for (final var entry : externalProcess.getAdditionalResultFiles().entrySet()) {
					final ZipEntry additionalFileEntry = new ZipEntry(entry.getKey());
					zipOut.putNextEntry(additionalFileEntry);
					zipOut.write(entry.getValue());
					zipOut.closeEntry();
				}
			}

			zipOut.finish();
		} catch (final IOException e) {
			throw new InternalIOException(InternalIOException.ZIP_CREATION,
			                              "Failed to create the ZIP file for starting an external process!", e);
		}
	}

	private Step getStep(final String stepName) throws BadStepNameException {
		final Step step = Step.getStep(stepName);
		if (step == null) {
			throw new BadStepNameException(BadStepNameException.NOT_FOUND,
			                               "The step '" + stepName + "' is not defined!");
		}
		return step;
	}

}
