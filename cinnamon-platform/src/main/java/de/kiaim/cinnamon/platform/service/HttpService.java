package de.kiaim.cinnamon.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.dto.ErrorResponse;
import de.kiaim.cinnamon.model.dto.ExternalProcessResponse;
import de.kiaim.cinnamon.model.serialization.mapper.YamlMapper;
import de.kiaim.cinnamon.platform.config.SerializationConfig;
import de.kiaim.cinnamon.platform.exception.InternalIOException;
import de.kiaim.cinnamon.platform.exception.InternalMissingHandlingException;
import de.kiaim.cinnamon.platform.exception.RequestRuntimeException;
import de.kiaim.cinnamon.platform.model.configuration.ExternalEndpoint;
import de.kiaim.cinnamon.platform.model.enumeration.StepInputEncoding;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
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

	/**
	 * Adds the given configuration to the body builder in the format specified by the given endpoint.
	 *
	 * @param configuration The configuration string.
	 * @param stepConfiguration Endpoint receiving the request.
	 * @param bodyBuilder   The body builder for the request body.
	 * @throws InternalIOException              If converting the configuration data into JSON failed.
	 * @throws InternalMissingHandlingException If no handling is specified for the given encoding.
	 */
	public void addConfig(final String configuration, final ExternalEndpoint stepConfiguration,
	                      final MultipartBodyBuilder bodyBuilder) throws InternalIOException, InternalMissingHandlingException {
		addConfig(configuration, stepConfiguration.getConfigurationEncoding(),
		          stepConfiguration.getConfigurationPartName(), bodyBuilder);
	}

	/**
	 * Adds the given configuration to the body builder in the specified format.
	 *
	 * @param configuration The configuration string.
	 * @param encoding      The target encoding for the request part.
	 * @param partName      The name of the request part.
	 * @param bodyBuilder   The body builder for the request body.
	 * @throws InternalIOException              If converting the configuration data into JSON failed.
	 * @throws InternalMissingHandlingException If no handling is specified for the given encoding.
	 */
	public void addConfig(final String configuration, final StepInputEncoding encoding, final String partName,
	                      final MultipartBodyBuilder bodyBuilder)
			throws InternalIOException, InternalMissingHandlingException {
		switch (encoding) {
			case FILE -> {
				addFile(configuration.getBytes(), "synthesizer_config.yaml", partName, bodyBuilder);
			}
			case JSON -> {
				//Convert yaml config to json for anonymization controller
				try {
					final String jsonConfig = YamlMapper.toJson(configuration);
					bodyBuilder.part(partName, jsonConfig, MediaType.APPLICATION_JSON);
				} catch (JsonProcessingException e) {
					throw new InternalIOException(InternalIOException.CONFIGURATION_SERIALIZATION,
					                              "Could not convert configuration from yaml to json!", e);
				}
			}
			default -> {
				throw new InternalMissingHandlingException(
						InternalMissingHandlingException.STEP_INPUT_ENCODING,
						"No handling for adding data set of type '" + encoding + "'!");
			}
		}
	}

	/**
	 * Adds the given file to the body builder.
	 *
	 * @param file        Content of the file.
	 * @param fileName     Name of the file.
	 * @param partName    The name of the request part.
	 * @param bodyBuilder The body builder for the request body.
	 */
	public void addFile(final byte[] file, final String fileName, final String partName,
	                    final MultipartBodyBuilder bodyBuilder) {
		bodyBuilder.part(partName,
		                 new ByteArrayResource(file) {
			                 @Override
			                 public String getFilename() {
				                 return fileName;
			                 }
		                 });
	}

}
