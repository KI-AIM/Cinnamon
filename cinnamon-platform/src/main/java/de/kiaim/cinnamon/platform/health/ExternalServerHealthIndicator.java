package de.kiaim.cinnamon.platform.health;

import de.kiaim.cinnamon.platform.model.configuration.ExternalServer;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health indicator that checks if an external server is healthy.
 *
 * @author Daniel Preciado-Marquez
 */
public class ExternalServerHealthIndicator implements HealthIndicator {

	private final Map<String, HealthContributor> healthContributors = new LinkedHashMap<>();

	/**
	 * The external server.
	 */
	private final ExternalServer externalServer;

	public ExternalServerHealthIndicator(final ExternalServer externalServer) {
		this.externalServer = externalServer;

		for (final var instance : externalServer.getInstances().values()) {
			final var healthIndicator = new ExternalServerInstanceHealthIndicator(instance);
			healthContributors.put(instance.getId(), healthIndicator);
		}
	}

	@Override
	public Health health() {
		int numHealthy = 0;
		int numUnknown = 0;

		final Map<String, Object> healthDetails = new LinkedHashMap<>();

		final Map<String, Health> instanceHealth = new LinkedHashMap<>();

		for (final Map.Entry<String, HealthContributor> entry : healthContributors.entrySet()) {
			if (entry.getValue() instanceof HealthIndicator indicator) {
				final Health health = indicator.health();
				instanceHealth.put(entry.getKey(), health);

				if (Status.UP.equals(health.getStatus())) {
					numHealthy++;
				} else if (Status.UNKNOWN.equals(health.getStatus())) {
					numUnknown++;
				}
			}
		}

		healthDetails.put("minUp", externalServer.getMinUp());
		healthDetails.put("numUp", numHealthy);
		healthDetails.put("instances", instanceHealth);

		final Health.Builder builder;

		if (numHealthy >= externalServer.getMinUp()) {
			builder = Health.up();
		} else if (numHealthy + numUnknown >= externalServer.getMinUp()) {
			builder = Health.unknown();
		} else {
			builder = Health.down();
		}

		return builder.withDetails(healthDetails).build();
	}
}
