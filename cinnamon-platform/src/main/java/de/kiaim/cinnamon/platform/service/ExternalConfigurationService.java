package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.model.dto.ErrorDetails;
import de.kiaim.cinnamon.platform.exception.BadConfigurationNameException;
import de.kiaim.cinnamon.platform.exception.InternalRequestException;
import de.kiaim.cinnamon.platform.exception.RequestRuntimeException;
import de.kiaim.cinnamon.platform.model.configuration.ExternalConfiguration;
import de.kiaim.cinnamon.platform.model.configuration.ExternalServer;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

/**
 * Service for forwarding request regarding configurations to the external server.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class ExternalConfigurationService {

	private final HttpService httpService;
	private final StepService stepService;

	public ExternalConfigurationService(final HttpService httpService, final StepService stepService) {
		this.httpService = httpService;
		this.stepService = stepService;
	}

	/**
	 * Fetches the available algorithms from the external server associated with the configuration.
	 *
	 * @param configurationName The configuration name associated with the external configuration.
	 * @return A YAML string with the available algorithms.
	 * @throws BadConfigurationNameException If the configuration name is not valid.
	 * @throws InternalRequestException      If the request fetching the algorithms failed.
	 */
	public String fetchAvailableAlgorithms(final String configurationName)
			throws BadConfigurationNameException, InternalRequestException {
		final ExternalConfiguration externalConfiguration = stepService.getExternalConfiguration(configurationName);
		final ExternalServer externalServer = externalConfiguration.getExternalServer();

		final String serverUrl = externalServer.getUrlServer();
		final String urlPath = externalConfiguration.getAlgorithmEndpoint();

		try {
			final WebClient webClient = WebClient.builder().baseUrl(serverUrl).build();
			return webClient.get()
			                .uri(urlPath)
			                .retrieve()
			                .onStatus(HttpStatusCode::isError,
			                          errorResponse -> errorResponse.toEntity(String.class)
			                                                        .map(httpService::buildErrorResponse))
			                .bodyToMono(String.class)
			                .block();
		} catch (final RequestRuntimeException e) {
			final String message = httpService.buildError(e, "fetch the status");
			final ErrorDetails errorDetails = new ErrorDetails().withConfigurationName(configurationName);
			throw new InternalRequestException(InternalRequestException.ALGORITHMS, message, errorDetails);
		} catch (WebClientRequestException e) {
			var message = "Failed to fetch available algorithms for configuration '" + configurationName + "'! " +
			              e.getMessage();
			final ErrorDetails errorDetails = new ErrorDetails().withConfigurationName(configurationName);
			throw new InternalRequestException(InternalRequestException.ALGORITHMS, message, errorDetails);
		}
	}

	/**
	 * Fetches the configuration definition from the external server for the given algorithm.
	 *
	 * @param configurationName Name of the configuration associated with the external configuration.
	 * @param definitionPath The path under which the configuration definition is available.
	 * @return A YAML string containing the configuration definition.
	 * @throws BadConfigurationNameException If the configuration name is not valid.
	 * @throws InternalRequestException      If the request fetching the definition failed.
	 */
	public String fetchAlgorithmDefinition(final String configurationName, final String definitionPath) throws BadConfigurationNameException, InternalRequestException {
		final ExternalConfiguration externalConfiguration = stepService.getExternalConfiguration(configurationName);
		final ExternalServer externalServer = externalConfiguration.getExternalServer();

		final String serverUrl = externalServer.getUrlServer();

		try {
			final WebClient webClient = WebClient.builder().baseUrl(serverUrl).build();
			return webClient.get()
			                .uri(definitionPath)
			                .retrieve()
			                .onStatus(HttpStatusCode::isError,
			                          errorResponse -> errorResponse.toEntity(String.class)
			                                                        .map(httpService::buildErrorResponse))
			                .bodyToMono(String.class)
			                .block();
		} catch (final RequestRuntimeException e) {
			final String message = httpService.buildError(e, "fetch the status");
			final ErrorDetails errorDetails = new ErrorDetails().withConfigurationName(configurationName);
			throw new InternalRequestException(InternalRequestException.ALGORITHMS, message, errorDetails);
		} catch (WebClientRequestException e) {
			var message = "Failed to fetch algorithms definition for configuration '" + configurationName +
			              "' and algorithm '" + definitionPath + "'! " + e.getMessage();

			final ErrorDetails errorDetails = new ErrorDetails().withConfigurationName(configurationName);
			throw new InternalRequestException(InternalRequestException.ALGORITHMS, message, errorDetails);
		}
	}

}
