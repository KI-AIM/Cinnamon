package de.kiaim.platform.helper;

import de.kiaim.platform.model.configuration.KiAimConfiguration;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Sets indices in the configuration.
 *
 * @author Daniel Preciado-Marquez
 */
@Component
public class KiAimConfigurationPostProcessor {

	private final KiAimConfiguration config;

	public KiAimConfigurationPostProcessor(final KiAimConfiguration config) {
		this.config = config;
	}

	@PostConstruct
	public void assignIndices() {
		for (int server = 0; server < config.getExternalServer().size(); server++) {
			config.getExternalServer().get(server).setIndex(server);
		}
		for (int endpoint = 0; endpoint < config.getExternalServerEndpoints().size(); endpoint++) {
			config.getExternalServerEndpoints().get(endpoint).setIndex(endpoint);
		}
		for (final var entry : config.getSteps().entrySet()) {
			entry.getValue().setStep(entry.getKey());
		}
	}

	@PostConstruct
	public void link() {
		// Link server and endpoints
		for (final var endpoint : config.getExternalServerEndpoints()) {
			final var serverIndex = endpoint.getExternalServerIndex();
			final var server = config.getExternalServer().get(serverIndex);
			server.getEndpoints().add(endpoint);
			endpoint.setServer(server);
		}

		// Link endpoints and steps
		for (final var step: config.getSteps().values()) {
			final var endpointIndex = step.getExternalServerEndpointIndex();
			final var endpoint = config.getExternalServerEndpoints().get(endpointIndex);
			step.setEndpoint(endpoint);
			endpoint.getSteps().add(step);
		}
	}
}
