package de.kiaim.platform.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.configuration.data.DateFormatConfiguration;
import de.kiaim.model.configuration.data.DateTimeFormatConfiguration;
import de.kiaim.model.data.DataRow;
import de.kiaim.model.data.DataSet;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.platform.config.SerializationConfig;
import de.kiaim.platform.config.StepConfiguration;
import de.kiaim.platform.exception.*;
import de.kiaim.platform.model.entity.ExternalProcessEntity;
import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.repository.ExternalProcessRepository;
import de.kiaim.platform.repository.ProjectRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
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
	private final StatusService statusService;
	private final StepService stepService;

	public ProcessService(final SerializationConfig serializationConfig,
	                      @Value("${server.port}") final int port,
	                      final ExternalProcessRepository externalProcessRepository,
	                      final ProjectRepository projectRepository,
	                      final DatabaseService databaseService,
	                      final StatusService statusService,
	                      final StepService stepService
	) {
		this.yamlMapper = serializationConfig.yamlMapper();

		this.port = port;
		this.externalProcessRepository = externalProcessRepository;
		this.projectRepository = projectRepository;
		this.databaseService = databaseService;
		this.statusService = statusService;
		this.stepService = stepService;
	}

	/**
	 * TODO Remove if actual APIs are implemented.
	 * Starts an external process.
	 * @param project The project the process corresponds to.
	 * @param stepName Name of the step in the configuration
	 * @param algorithmName The name of the algorithm.
	 */
	@Transactional
	public void startProcessTest(final ProjectEntity project, final String stepName, final String algorithmName)
			throws BadStepNameException {
		// Set process entity
		final ExternalProcessEntity externalProcess = new ExternalProcessEntity();
		project.setExternalProcess(externalProcess);
		projectRepository.save(project);

		// Get configuration
		final String url = stepService.getStepConfiguration(stepName).getUrl();

		// Create request
		final WebClient webClient = WebClient.builder().baseUrl(url).build();
		final WebClient.UriSpec<WebClient.RequestBodySpec> uriSpec = webClient.post();
		final WebClient.RequestBodySpec bodySpec = uriSpec.uri("/start_synthetization_process/" + algorithmName);

		final LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		map.add("path", "/app/inputs/input_files_ctgan_kiaim.zip");
		map.add("session_key", externalProcess.getId().toString());

		final WebClient.RequestHeadersSpec<?> headersSpec = bodySpec.body(BodyInserters.fromFormData(map));
		final WebClient.ResponseSpec responseSpec = headersSpec.retrieve();

		// TODO What should we get?
		final String response = responseSpec.bodyToMono(String.class).block();

		// Update status
		statusService.setExternalProcessingStatus(project, ProcessStatus.RUNNING);
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
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 * @throws InternalIOException If the request body could not be created.
	 * @throws InternalRequestException If the request to start the process failed.
	 */
	@Transactional
	public void startProcess(final ProjectEntity project, final String stepName, final String url,
	                         final String configuration)
			throws BadColumnNameException, BadDataSetIdException, BadStepNameException, InternalDataSetPersistenceException, InternalIOException, InternalRequestException {
		// Set process entity
		final ExternalProcessEntity externalProcess = new ExternalProcessEntity();
		project.setExternalProcess(externalProcess);
		projectRepository.save(project);

		// Get configuration
		final StepConfiguration stepConfiguration = stepService.getStepConfiguration(stepName);
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
			bodyBuilder.part("attribute_config", new FileSystemResource(new File("C:\\Users\\danie\\Desktop\\input_files\\attribute_config.yaml")));
			// TODO does not work because of data format configurations
//			bodyBuilder.part("attribute_config", new ByteArrayResource(yamlMapper.writeValueAsString(dataSet.getDataConfiguration()).getBytes()) {
//				@Override
//				public String getFilename() {
//					return "attribute_config.yaml";
//				}
//			});
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
		                              .bodyToMono(String.class)
		                              .onErrorComplete()
		                              .block();
		if (apiException.get() != null) {
			throw apiException.get();
		}

		// Update status
		statusService.setExternalProcessingStatus(project, ProcessStatus.RUNNING);
	}

	/**
	 * Finishes the process with the given process ID.
	 * Sets the status to 'finished' and deletes the ExternalProcess object.
	 * @param processId The ID of the process to finish.
	 * @throws BadProcessIdException If the given process ID is not valid.
	 */
	@Transactional
	public void finishProcess(final Long processId) throws BadProcessIdException {
		final Optional<ExternalProcessEntity> process = externalProcessRepository.findById(processId);

		if (process.isEmpty()) {
			throw new BadProcessIdException(BadProcessIdException.NO_PROCESS,
			                                "No process with the given ID '" + processId +
			                                "' exists! Maybe it was canceled!");
		}

		// Invalid processID
		final ProjectEntity project = process.get().getProject();

		// Update status
		statusService.setExternalProcessingStatus(project, ProcessStatus.FINISHED);

		project.setExternalProcess(null);
		projectRepository.save(project);
	}

	/**
	 * Writes a ZIP to the given OutputStream containing the data set and data configuration of the given project and the given configuration.
	 * @param project The project of the data set.
	 * @param outputStream The OutputStream to write to.
	 * @param configuration The process-specific configuration to be included.
	 * @throws BadColumnNameException If the data set does not contain a column with the given names.
	 * @throws BadDataSetIdException If no DataConfiguration is associated with the given project.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 * @throws InternalIOException If the request body could not be created.
	 */
	public void createZipFile(final ProjectEntity project, final OutputStream outputStream, final String configuration)
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
			final ZipEntry configZipEntry = new ZipEntry("synthesizer_config.yaml");
			zipOut.putNextEntry(configZipEntry);
			zipOut.write(configuration.getBytes());
			zipOut.closeEntry();

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

			zipOut.finish();
		} catch (final IOException e) {
			throw new InternalIOException(InternalIOException.ZIP_CREATION,
			                              "Failed to create the ZIP file for starting an external process!", e);
		}
	}

}
