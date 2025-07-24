package de.kiaim.cinnamon.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.dto.ErrorResponse;
import de.kiaim.cinnamon.model.dto.ExternalProcessResponse;
import de.kiaim.cinnamon.platform.config.SerializationConfig;
import de.kiaim.cinnamon.platform.exception.RequestRuntimeException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Service class for HTTP requests.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class HttpService {

	private final ObjectMapper jsonMapper;

	public HttpService(final SerializationConfig serializationConfig) {
		this.jsonMapper = serializationConfig.jsonMapper();
	}

	/**
	 * Creates an exception from the given response.
	 *
	 * @param response The response.
	 * @return Exception containing the response.
	 */
	public RequestRuntimeException buildErrorResponse(final ResponseEntity<String> response) {
		ExternalProcessResponse responseBody;
		try {
			responseBody = jsonMapper.readValue(response.getBody(), ExternalProcessResponse.class);
		} catch (JsonProcessingException e) {

			try {
				ErrorResponse errorResponse = jsonMapper.readValue(response.getBody(), ErrorResponse.class);
				responseBody = new ExternalProcessResponse();
				responseBody.setError(errorResponse.getErrorMessage());
			} catch (JsonProcessingException e1) {
				responseBody = new ExternalProcessResponse();
				responseBody.setError(response.getBody());
			}
		}

		final ResponseEntity<ExternalProcessResponse> responseEntity = ResponseEntity.status(response.getStatusCode())
		                                                                             .headers(response.getHeaders())
		                                                                             .body(responseBody);
		return new RequestRuntimeException(responseEntity);
	}

	/**
	 * Builds an error message from the given exception
	 *
	 * @param e      The exception.
	 * @param action String describing the purpose of the failed request, appended to "Failed to".
	 * @return The error message.
	 */
	public String buildError(final RequestRuntimeException e, final String action) {
		var message = "Failed to " + action + "! Got status of '" + e.getResponse().getStatusCode() + "'.";
		if (e.getResponse().getBody() != null) {
			if (e.getResponse().getBody().getMessage() != null) {
				message += " Got message: '" + e.getResponse().getBody().getMessage() + "'.";
			}
			if (e.getResponse().getBody().getError() != null) {
				message += " Got error: '" + e.getResponse().getBody().getError() + "'.";
			}
		}
		return message;
	}

}
