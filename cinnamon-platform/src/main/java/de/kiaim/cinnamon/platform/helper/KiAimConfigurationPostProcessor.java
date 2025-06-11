package de.kiaim.cinnamon.platform.helper;

import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Sets indices in the configuration.
 *
 * @author Daniel Preciado-Marquez
 */
@Component
public class KiAimConfigurationPostProcessor {

	private final CinnamonConfiguration config;

	public KiAimConfigurationPostProcessor(final CinnamonConfiguration config) {
		this.config = config;
	}

	@PostConstruct
	public void assignIndices() {
		// Set indices of external stuff
		for (final var externalServer : config.getExternalServer().entrySet()) {
			externalServer.getValue().setName(externalServer.getKey());
		}
		for (final var externalConfiguration : config.getExternalConfiguration().entrySet()) {
			externalConfiguration.getValue().setConfigurationName(externalConfiguration.getKey());
		}
		for (final var endpoint : config.getExternalServerEndpoints().entrySet()) {
			endpoint.getValue().setIndex(endpoint.getKey());
		}

		// Set names of jobs/stages
		for (final var entry : config.getSteps().entrySet()) {
			entry.getValue().setName(entry.getKey());
		}
		for (final var entry : config.getStages().entrySet()) {
			entry.getValue().setStageName(entry.getKey());
		}
	}

	@PostConstruct
	public void link() {
		// Link server and endpoints
		for (final var endpoint : config.getExternalServerEndpoints().values()) {
			final var serverIndex = endpoint.getExternalServerName();
			final var server = config.getExternalServer().get(serverIndex);
			server.getEndpoints().add(endpoint);
			endpoint.setServer(server);

			final var configurationIndex = endpoint.getConfigurationName();
			final var configuration = config.getExternalConfiguration().get(configurationIndex);
			endpoint.setConfiguration(configuration);
		}

		// Link server and configurations
		for (final var externalConfig : config.getExternalConfiguration().values()) {
			final var server = config.getExternalServer().get(externalConfig.getExternalServerName());
			externalConfig.setExternalServer(server);
		}

		// Link endpoints and steps
		for (final var step: config.getSteps().values()) {
			final var endpointIndex = step.getExternalServerEndpointIndex();
			final var endpoint = config.getExternalServerEndpoints().get(endpointIndex);
			step.setEndpoint(endpoint);
		}

		// Link pipeline and stages
		for (final var stageName : config.getPipeline().getStages()) {
			config.getPipeline().getStageList().add(config.getStages().get(stageName));
		}

		// Link stages and jobs
		for (final var stage : config.getStages().values()) {
			for (final var jobName : stage.getJobs()) {
				stage.getJobList().add(config.getSteps().get(jobName));
			}
		}

	}
}
