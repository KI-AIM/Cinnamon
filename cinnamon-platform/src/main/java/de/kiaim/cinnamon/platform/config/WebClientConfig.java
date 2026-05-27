package de.kiaim.cinnamon.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.spring.CustomMediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for a custom WebClient with support for JSON and YAML.
 *
 * @author Daniel Preciado-Marquez
 */
@Configuration
public class WebClientConfig {

	@Bean(name = "multiFormatWebClient")
	public WebClient yamlWebClient(final SerializationConfig serializationConfig) {
		final ObjectMapper jsonMapper = serializationConfig.jsonMapper();
		final ObjectMapper yamlMapper = serializationConfig.yamlMapper();

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
	                                 final ObjectMapper jsonMapper,
	                                 final ObjectMapper yamlMapper) {
		configurer.customCodecs().register(
				new Jackson2JsonDecoder(jsonMapper, MediaType.APPLICATION_JSON)
		);
		configurer.customCodecs().register(
				new Jackson2JsonEncoder(jsonMapper, MediaType.APPLICATION_JSON)
		);

		configurer.customCodecs().register(
				new Jackson2JsonDecoder(yamlMapper,
				                        MediaType.APPLICATION_YAML,
				                        MediaType.APPLICATION_OCTET_STREAM,
				                        CustomMediaType.TEXT_YAML,
				                        CustomMediaType.APPLICATION_X_YAML)
		);
		configurer.customCodecs().register(
				new Jackson2JsonEncoder(yamlMapper,
				                        MediaType.APPLICATION_YAML,
				                        CustomMediaType.TEXT_YAML,
				                        CustomMediaType.APPLICATION_X_YAML)
		);
	}
}
