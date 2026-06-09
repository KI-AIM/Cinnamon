package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.model.configuration.data.DataSourceServerConfiguration;
import de.kiaim.cinnamon.model.configuration.data.file.FileType;
import de.kiaim.cinnamon.model.enumeration.DataSourceType;
import de.kiaim.cinnamon.model.spring.CustomMediaType;
import de.kiaim.cinnamon.platform.exception.InternalRequestException;
import de.kiaim.cinnamon.platform.exception.RequestRuntimeException;
import de.kiaim.cinnamon.platform.helper.StringMultipartFile;
import de.kiaim.cinnamon.platform.processor.source.DataSourceProcessor;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Service for communicating with a FHIR server.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class FhirServerService implements DataSourceProcessor {

	private final HttpService httpService;

	public FhirServerService(final HttpService httpService) {
		this.httpService = httpService;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DataSourceType getSupportedDataSourceType() {
		return DataSourceType.FHIR_SERVER;
	}

	/**
	 * {@inheritDoc}
	 * Fetches a FHIR bundle from the given FHIR server.
	 */
	@Override
	public Pair<FileType, MultipartFile> retrieveFile(
			final DataSourceServerConfiguration config
	) throws InternalRequestException {
		final String content;
		try {
			final WebClient webClient = WebClient.builder().baseUrl(config.getUrl()).build();
			content = webClient.get()
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

		var file = new StringMultipartFile(content, "fhir_bundle.json", CustomMediaType.APPLICATION_FHIR_JSON);
		return Pair.of(FileType.FHIR, file);
	}
}
