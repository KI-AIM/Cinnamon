package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.model.configuration.data.DataSourceServerConfiguration;
import de.kiaim.cinnamon.platform.exception.InternalRequestException;
import de.kiaim.cinnamon.platform.exception.RequestRuntimeException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Service for communicating with a FHIR server.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class FhirServerService {

	private final HttpService httpService;

	public FhirServerService(final HttpService httpService) {
		this.httpService = httpService;
	}

	/**
	 * Fetches a FHIR bundle from the given FHIR server.
	 *
	 * @param config The configuration of the FHIR server.
	 * @return The FHIR bundle.
	 * @throws InternalRequestException If fetching the FHIR bundle failed.
	 */
	public String getFhirBundle(final DataSourceServerConfiguration config) throws InternalRequestException {
		try {
			final WebClient webClient = WebClient.builder().baseUrl(config.getUrl()).build();
			return webClient.get()
			                .accept(MediaType.APPLICATION_JSON)
			                .retrieve()
			                .onStatus(HttpStatusCode::isError,
			                          errorResponse -> errorResponse.toEntity(String.class)
			                                                        .map(httpService::buildErrorResponse))
			                .bodyToMono(String.class)
			                .block();
		} catch (final RequestRuntimeException e) {
			final String message = httpService.buildError(e, "fetch FHIR bundle");
			throw new InternalRequestException(InternalRequestException.ALGORITHMS, message, e);
		} catch (final Exception e) {
			var message = "Failed to fetch FHIR bundle! " + e.getMessage();
			throw new InternalRequestException(InternalRequestException.ALGORITHMS, message, e);
		}
	}

}
