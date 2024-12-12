package de.kiaim.platform.service;

import de.kiaim.model.dto.ExternalProcessResponse;
import de.kiaim.platform.model.configuration.KiAimConfiguration;
import de.kiaim.platform.model.configuration.StepConfiguration;
import de.kiaim.platform.model.configuration.StepInputConfiguration;
import de.kiaim.platform.exception.InternalDataSetPersistenceException;
import de.kiaim.platform.exception.InternalIOException;
import de.kiaim.platform.exception.InternalRequestException;
import de.kiaim.platform.exception.RequestRuntimeException;
import de.kiaim.platform.model.entity.DataSetEntity;
import de.kiaim.platform.model.entity.LobWrapperEntity;
import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.enumeration.Step;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Service for statistics.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class StatisticsService {

	public static final String ID_PREFIX = "statistics_";

	private final int port;

	private final Resource statisticsResource;
	private final KiAimConfiguration kiAimConfiguration;
	private final DatabaseService databaseService;
	private final DataSetService dataSetService;
	private final ProcessService processService;
	private final ProjectService projectService;

	public StatisticsService(
			@Value("classpath:organized_resemblance_metrics_synthetic.yaml") final Resource statisticsResource,
			@Value("${server.port}") final int port,
			final KiAimConfiguration kiAimConfiguration,
			final DatabaseService databaseService,
			final DataSetService dataSetService,
			final ProcessService processService,
			final ProjectService projectService
	) {
		this.port = port;
		this.statisticsResource = statisticsResource;
		this.kiAimConfiguration = kiAimConfiguration;
		this.databaseService = databaseService;
		this.processService = processService;
		this.projectService = projectService;
		this.dataSetService = dataSetService;
	}

	/**
	 * Returns the statistics.
	 * @return The Statistics.
	 */
	@Transactional
	@Nullable
	public String getStatistics(final ProjectEntity project) throws InternalIOException, InternalDataSetPersistenceException, InternalRequestException {
		final var rawData = project.getOriginalData().getStatistics();
		String data = "";

		if (rawData == null) {
			calculateStatistics(project.getOriginalData().getDataSet());
			project.getOriginalData().setStatistics(new LobWrapperEntity(""));
			projectService.saveProject(project);

//			try {
//				data = statisticsResource.getContentAsString(StandardCharsets.UTF_8);
//				project.getOriginalData().setStatistics(new LobWrapperEntity(data));
//				projectService.saveProject(project);
//			} catch (IOException e) {
//				throw new InternalIOException("N/A", "Failed to load statistics file!", e);
//			}
		} else {
			data = rawData.getLobString();
		}

		return data;
	}

	/**
	 * TODO include in process service
	 * @return String containing the statistics.
	 */
	@Transactional
	public void finishStatistics(final String key, final MultipartFile metrics) throws InternalIOException {
		var ids = key.substring(ID_PREFIX.length());
		var id = Long.parseLong(ids);

		DataSetEntity d = dataSetService.getDataSetEntity(id);

		try {
			String data = IOUtils.toString(metrics.getInputStream(), StandardCharsets.UTF_8);
			d.getOriginalData().setStatistics(new LobWrapperEntity(data));
			projectService.saveProject(d.getOriginalData().getProject());
		} catch (IOException e) {
			throw new InternalIOException("", "", e);
		}
	}

	/**
	 * TODO include in process service
	 * @param datasetEntity The data set to calculate the statistics for.
	 */
	private void calculateStatistics(
			final DataSetEntity datasetEntity) throws InternalDataSetPersistenceException, InternalIOException, InternalRequestException {

		// Prepare body
		final MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

		// Add configured input data sets
		final var dataset = databaseService.exportDataSet(datasetEntity);
		var c = new StepInputConfiguration();
		c.setPartName("real_data");
		c.setFileName("real_data.csv");
		c.setDataConfigurationName("attribute_config");
		processService.addDataSetFile(bodyBuilder, c, dataset);

		final String requestId = ID_PREFIX + datasetEntity.getId().toString();
		bodyBuilder.part("session_key", requestId);
		final StepConfiguration stepConfiguration = kiAimConfiguration.getSteps().get(Step.TECHNICAL_EVALUATION);
		final String callbackHost = stepConfiguration.getCallbackHost();
		final var serverAddress = ServletUriComponentsBuilder.fromCurrentContextPath()
		                                                     .host(callbackHost)
		                                                     .port(this.port)
		                                                     .build()
		                                                     .toUriString();

		bodyBuilder.part("callback",
		                 serverAddress + "/api/process/" + requestId + "/callback");

		// Do the request
		try {
			final String serverUrl = stepConfiguration.getUrl();
			final String url = "/calculate_descriptive_statistics";
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
		} catch (RequestRuntimeException e) {
			var message = "Failed to start the process! Got status of " + e.getResponse().getStatusCode();
			if (e.getResponse().getBody() != null) {
				message += " with message: '" + e.getResponse().getBody().getMessage() + "' and error: '" +
				           e.getResponse().getBody().getError() + "'";
			}
			throw new InternalRequestException(InternalRequestException.PROCESS_START, message);
		} catch (WebClientRequestException e) {
			final var message = "Failed to start the process! " + e.getMessage();
			throw new InternalRequestException(InternalRequestException.PROCESS_START, message);
		}
	}
}
