package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.model.dto.ModuleReportContent;
import de.kiaim.cinnamon.platform.exception.InternalIOException;
import de.kiaim.cinnamon.platform.exception.InternalMissingHandlingException;
import de.kiaim.cinnamon.platform.exception.InternalRequestException;
import de.kiaim.cinnamon.platform.exception.RequestRuntimeException;
import de.kiaim.cinnamon.platform.model.configuration.*;
import de.kiaim.cinnamon.platform.model.entity.ExecutionStepEntity;
import de.kiaim.cinnamon.platform.model.entity.ExternalProcessEntity;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.enumeration.StepInputEncoding;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.util.HashMap;
import java.util.Map;

/**
 * Service class for functionalities regarding the report.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class ReportService {

	private final CinnamonConfiguration cinnamonConfiguration;

	private final DataProcessorService dataProcessorService;
	private final ExternalServerInstanceService externalServerInstanceService;
	private final HttpService httpService;

	public ReportService(final CinnamonConfiguration cinnamonConfiguration,
	                     final DataProcessorService dataProcessorService,
	                     final ExternalServerInstanceService externalServerInstanceService,
	                     final HttpService httpService) {
		this.cinnamonConfiguration = cinnamonConfiguration;
		this.dataProcessorService = dataProcessorService;
		this.externalServerInstanceService = externalServerInstanceService;
		this.httpService = httpService;
	}

	/**
	 * Fetches the report content for all jobs in the pipeline.
	 * The map contains one entry for each job, where the name of the job is used as the key.
	 *
	 * @param project The project to fetch the data for.
	 * @return Map containing the report data for each job.
	 * @throws InternalIOException              If converting the request data into JSON failed.
	 * @throws InternalMissingHandlingException If generating the request content failed.
	 * @throws InternalRequestException         If making the request failed.
	 */
	@Transactional
	public Map<String, ModuleReportContent> fetchReportData(final ProjectEntity project)
			throws InternalIOException, InternalMissingHandlingException, InternalRequestException {
		final Map<String, ModuleReportContent> reportData = new HashMap<>();

		for (final ExecutionStepEntity executionStep : project.getPipelines().get(0).getStages()) {
			for (final ExternalProcessEntity externalProcess : executionStep.getProcesses()) {
				if (externalProcess.isSkip()) {
					reportData.put(externalProcess.getJob().getName(), null);
				} else {
					reportData.put(externalProcess.getJob().getName(), fetchReportData(externalProcess));
				}
			}
		}

		return reportData;
	}

	/**
	 * Fetches the report content for the given external process.
	 *
	 * @param externalProcess The external process.
	 * @return The report data for the job.
	 * @throws InternalIOException              If converting the request data into JSON failed.
	 * @throws InternalMissingHandlingException If generating the request content failed.
	 * @throws InternalRequestException         If making the request failed.
	 */
	@Nullable
	private ModuleReportContent fetchReportData(final ExternalProcessEntity externalProcess)
			throws InternalIOException, InternalMissingHandlingException, InternalRequestException {
		final ExternalEndpoint endpoint = cinnamonConfiguration.getExternalServerEndpoints()
		                                                       .get(externalProcess.getEndpoint());
		final ExternalServer server = endpoint.getServer();

		final String urlPath = server.getReportEndpoint();
		if (urlPath == null) {
			return null;
		}

		final ExternalServerInstance instance = externalServerInstanceService.findAvailableExternalServerInstance(
				server, true);
		if (instance == null) {
			throw new InternalRequestException(InternalRequestException.NO_INSTANCE_AVAILABLE,
			                                   "No available external server instance found for '" + server.getName() +
			                                   "'!");
		}

		final String serverUrl = instance.getUrl();

		// Prepare body
		final MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

		// Add config
		final String configuration = externalProcess.getConfigurationString();
		if (configuration != null) {
			httpService.addConfig(configuration, StepInputEncoding.JSON, "configuration", bodyBuilder);
		}

		// Add results
		final var results = externalProcess.getResultFiles().entrySet();
		for (final var result : results) {
			final String partName = dataProcessorService.getFileNameWithoutExtension(result.getKey());
			httpService.addFile(result.getValue().getLob(), result.getKey(), partName, bodyBuilder);
		}

		try {
			final WebClient webClient = WebClient.builder().baseUrl(serverUrl).build();
			return webClient.post()
			                .uri(urlPath)
			                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
			                .retrieve()
			                .onStatus(HttpStatusCode::isError,
			                          errorResponse -> errorResponse.toEntity(String.class)
			                                                        .map(httpService::buildErrorResponse))
			                .bodyToMono(ModuleReportContent.class)
			                .block();
		} catch (final RequestRuntimeException e) {
			final String message = httpService.buildError(e, "fetch the report data");
			throw new InternalRequestException(InternalRequestException.REPORT_DATA, message);
		} catch (WebClientRequestException e) {
			var message = "Failed to fetch the report data for '" + server.getName() + "'! " + e.getMessage();
			throw new InternalRequestException(InternalRequestException.REPORT_DATA, message);
		}
	}

}
