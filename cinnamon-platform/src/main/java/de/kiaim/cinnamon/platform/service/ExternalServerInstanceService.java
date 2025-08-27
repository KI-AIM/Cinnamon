package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.platform.model.configuration.ExternalServer;
import de.kiaim.cinnamon.platform.model.configuration.ExternalServerInstance;
import de.kiaim.cinnamon.platform.repository.BackgroundProcessRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

/**
 * Service for everything regarding extern server instances.
 *
 * @author Daniel Preciado-Marquez
 */
@Log4j2
@Service
public class ExternalServerInstanceService {

	private final BackgroundProcessRepository backgroundProcessRepository;

	public ExternalServerInstanceService(final BackgroundProcessRepository backgroundProcessRepository) {
		this.backgroundProcessRepository = backgroundProcessRepository;
	}

	/**
	 * Returns an available server instance of the given external server or null.
	 *
	 * @param externalServer The external server.
	 * @param ignoreMaxParallelProcess If the max number of processes should be checked.
	 * @return The instance.
	 */
	@Nullable
	public ExternalServerInstance findAvailableExternalServerInstance(final ExternalServer externalServer,
	                                                                  final boolean ignoreMaxParallelProcess) {
		ExternalServerInstance target = null;

		final List<Pair<ExternalServerInstance, Long>> instances = new ArrayList<>(externalServer.getInstances().size());

		// Get the number of running processes per instance
		for (final ExternalServerInstance instance : externalServer.getInstances().values()) {
			var count = backgroundProcessRepository.countByServerInstance(instance.getId());
			instances.add(Pair.of(instance, count));
		}

		// Sort by the number of running processes
		instances.sort(Comparator.comparing(Pair::getSecond));

		for (final Pair<ExternalServerInstance, Long> instance : instances) {
			// Check if capacities are available for this instance
			if (instance.getFirst().getMaxParallelProcess() >= 0) {
				if (instance.getSecond() >= instance.getFirst().getMaxParallelProcess()) {
					continue;
				}
			}

			// Check if the instance is available and healthy
			if (externalServer.getMinUp() != externalServer.getInstances().size()) {
				if (!isExternalServerInstanceAvailable(instance.getFirst())) {
					continue;
				}
			}

			target = instance.getFirst();
			break;
		}

		return target;
	}

	/**
	 * Checks if the instance is healthy.
	 *
	 * @param instance The instance to be checked.
	 * @return If the instance is available and healthy.
	 */
	private boolean isExternalServerInstanceAvailable(final ExternalServerInstance instance) {
		final ExternalServer server = instance.getServer();
		final String serverUrl = instance.getUrl();
		final String healthEndpoint = server.getHealthEndpoint();

		try {
			final WebClient webClient = WebClient.builder().baseUrl(serverUrl).build();
			var response = webClient.method(HttpMethod.GET)
			                        .uri(healthEndpoint)
			                        .retrieve()
			                        .bodyToMono(Map.class)
			                        .timeout(Duration.ofMillis(instance.getHealthTimeout()))
			                        .block();

			return response != null && response.get("status").equals("UP");
		} catch (final Exception e) {
			log.warn("External server instance '{}' is not available!", instance.getId(), e);
			return false;
		}
	}


}
