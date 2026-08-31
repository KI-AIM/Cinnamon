package de.kiaim.cinnamon.platform.config;

import de.kiaim.cinnamon.model.spring.CustomMediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Configuration for a custom WebClient with support for JSON and YAML.
 *
 * @author Daniel Preciado-Marquez
 */
@Configuration
public class WebClientConfig {

	@Bean(name = "multiFormatWebClient")
	public WebClient yamlWebClient(final SerializationConfig serializationConfig) {
		final JsonMapper jsonMapper = serializationConfig.jsonMapper();
		final YAMLMapper yamlMapper = serializationConfig.yamlMapper();

		final ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
		                                                                .codecs(configurer -> configureYamlCodecs(
				                                                                configurer,
				                                                                jsonMapper,
				                                                                yamlMapper))
		                                                                .build();

		return WebClient.builder()
		                .exchangeStrategies(exchangeStrategies)
		                .build();
	}

	private void configureYamlCodecs(final ClientCodecConfigurer configurer,
	                                 final JsonMapper jsonMapper,
	                                 final YAMLMapper yamlMapper) {
		configurer.customCodecs().register(
				new JacksonJsonDecoder(jsonMapper, MediaType.APPLICATION_JSON)
		);
		configurer.customCodecs().register(
				new JacksonJsonEncoder(jsonMapper, MediaType.APPLICATION_JSON)
		);

		configurer.customCodecs().register(
				new YamlDecoder(yamlMapper,
				                MediaType.APPLICATION_YAML,
				                MediaType.APPLICATION_OCTET_STREAM,
				                CustomMediaType.TEXT_YAML,
				                CustomMediaType.APPLICATION_X_YAML)
		);
		configurer.customCodecs().register(
				new YamlEncoder(yamlMapper,
				                MediaType.APPLICATION_YAML,
				                CustomMediaType.TEXT_YAML,
				                CustomMediaType.APPLICATION_X_YAML)
		);
	}
}
