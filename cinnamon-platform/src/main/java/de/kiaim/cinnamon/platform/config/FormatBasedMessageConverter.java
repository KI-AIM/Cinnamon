package de.kiaim.cinnamon.platform.config;

import de.kiaim.cinnamon.model.spring.CustomMediaType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidDefinitionException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class FormatBasedMessageConverter extends AbstractHttpMessageConverter<Object> {

	private final Map<MediaType, ObjectMapper> supportedMediaTypes = new HashMap<>();

	@Value("${springdoc.api-docs.path}")
	private String openApiPath;

	private final HttpServletRequest request;

	public FormatBasedMessageConverter(final SerializationConfig serializationConfig, HttpServletRequest request) {
		super(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON, CustomMediaType.APPLICATION_X_YAML,
		      MediaType.APPLICATION_YAML, CustomMediaType.TEXT_YAML);

		supportedMediaTypes.put(MediaType.APPLICATION_JSON, serializationConfig.jsonMapper());
		supportedMediaTypes.put(MediaType.APPLICATION_PROBLEM_JSON, serializationConfig.jsonMapper());

		final ObjectMapper yamlMapper = serializationConfig.yamlMapper();
		supportedMediaTypes.put(CustomMediaType.APPLICATION_X_YAML, yamlMapper);
		supportedMediaTypes.put(MediaType.APPLICATION_YAML, yamlMapper);
		supportedMediaTypes.put(CustomMediaType.TEXT_YAML, yamlMapper);

		this.request = request;
	}

	@Override
	protected boolean supports(final Class<?> clazz) {
		return true;
	}

	@Override
	protected boolean canWrite(final MediaType mediaType) {
		if (mediaType != null && mediaType.includes(MediaType.APPLICATION_JSON)) {
			final String path = request.getRequestURI();
			if (path.startsWith(openApiPath)) {
				return false;
			}
		}

		return super.canWrite(mediaType);
	}

	@Override
	protected Object readInternal(final Class<?> clazz, final HttpInputMessage inputMessage)
			throws IOException, HttpMessageNotReadableException {
		final MediaType mediaType = inputMessage.getHeaders().getContentType();
		final ObjectMapper objectMapper = getObjectMapper(mediaType);
		try {
			return objectMapper.readValue(inputMessage.getBody(), clazz);
		} catch (final InvalidDefinitionException ex) {
			throw new HttpMessageConversionException("Type definition error: " + ex.getType(), ex);
		} catch (final JacksonException ex) {
			// Jackson 3 exceptions are unchecked and no longer extend IOException, so they are not
			// wrapped into a HttpMessageNotReadableException by the caller. Wrap them here instead.
			throw new HttpMessageNotReadableException("JSON parse error: " + ex.getOriginalMessage(), ex,
			                                          inputMessage);
		}
	}

	@Override
	protected void writeInternal(final Object o, final HttpOutputMessage outputMessage)
			throws IOException, HttpMessageNotWritableException {
		final MediaType mediaType = outputMessage.getHeaders().getContentType();
		final ObjectMapper objectMapper = getObjectMapper(mediaType);
		objectMapper.writeValue(outputMessage.getBody(), o);
	}

	private ObjectMapper getObjectMapper(final MediaType mediaType) {
		return supportedMediaTypes.getOrDefault(mediaType, supportedMediaTypes.get(CustomMediaType.APPLICATION_X_YAML));
	}
}
