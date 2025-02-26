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
		for (final var externalServer : config.getExternalServer().entrySet()) {
			externalServer.getValue().setIndex(externalServer.getKey());
		}
		for (final var endpoint : config.getExternalServerEndpoints().entrySet()) {
			endpoint.getValue().setIndex(endpoint.getKey());
		}

		for (final var entry : config.getSteps().entrySet()) {
			entry.getValue().setStep(entry.getKey());
		}
	}

	@PostConstruct
	public void link() {
		// Link server and endpoints
		for (final var endpoint : config.getExternalServerEndpoints().values()) {
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
