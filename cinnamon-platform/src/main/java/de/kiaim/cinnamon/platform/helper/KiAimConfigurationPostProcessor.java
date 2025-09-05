package de.kiaim.cinnamon.platform.helper;

import de.kiaim.cinnamon.platform.exception.InternalApplicationConfigurationException;
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
	public void init() throws InternalApplicationConfigurationException {
		assignIndices();
		link();
	}

	private void assignIndices() {
		// Set indices of external stuff
		for (final var externalServer : config.getExternalServer().entrySet()) {
			externalServer.getValue().setName(externalServer.getKey());
			for (final var externalServerInstance : externalServer.getValue().getInstances().entrySet()) {
				externalServerInstance.getValue().setName(externalServerInstance.getKey());
			}
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

		// Set names of hosts
		for (final var entry : config.getExternalHost().entrySet()) {
			entry.getValue().setName(entry.getKey());
		}
	}

	private void link() throws InternalApplicationConfigurationException {
		// Link server and instance
		for (final var server : config.getExternalServer().values()) {
			for (final var instance : server.getInstances().values()) {
				instance.setServer(server);

				// Link hosts and instances
				final var host = config.getExternalHost().get(instance.getHostName());
				host.getInstances().add(instance);
				instance.setHost(host);
			}
		}

		// Link server and endpoints
		for (final var endpoint : config.getExternalServerEndpoints().values()) {
			final var serverIndex = endpoint.getExternalServerName();
			final var server = config.getExternalServer().get(serverIndex);
			server.getEndpoints().add(endpoint);
			endpoint.setServer(server);

			final var configurationIndex = endpoint.getConfigurationName();
			if (configurationIndex == null || configurationIndex.isBlank()) {
				continue;
			}

			final var configuration = config.getExternalConfiguration().get(configurationIndex);
			configuration.getUsages().add(endpoint);
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
			endpoint.getUsages().add(step);
			step.setEndpoint(endpoint);
		}

		// Link pipeline and stages
		for (final var stageName : config.getPipeline().getStages()) {
			config.getPipeline().getStageList().add(config.getStages().get(stageName));
		}

		// Link stages and jobs
		for (final var stage : config.getStages().values()) {
			for (final var jobName : stage.getJobs()) {
				final var job = config.getSteps().get(jobName);
				stage.getJobList().add(job);

				if (job.getStage() != null) {
					throw new InternalApplicationConfigurationException(
							InternalApplicationConfigurationException.MULTIPLE_JOB_USAGE,
							"The job '" + job.getName() + "' is used multiple times!");
				}

				job.setStage(stage);
			}
		}

	}
}
