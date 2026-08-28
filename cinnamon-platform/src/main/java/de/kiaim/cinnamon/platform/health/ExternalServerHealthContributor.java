package de.kiaim.cinnamon.platform.health;

import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import org.springframework.boot.health.contributor.CompositeHealthContributor;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.health.contributor.HealthContributors;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Health contributor that checks if all external servers are healthy.
 *
 * @author Daniel Preciado-Marquez
 */
@Component
@DependsOn({"cinnamonConfigurationPostProcessor"})
public class ExternalServerHealthContributor implements CompositeHealthContributor {

	private final Map<String, HealthContributor> healthContributors = new LinkedHashMap<>();

	public ExternalServerHealthContributor(final CinnamonConfiguration cinnamonConfiguration) {
		for (final var externalServer : cinnamonConfiguration.getExternalServer().entrySet()) {
			final var healthIndicator = new ExternalServerHealthIndicator(externalServer.getValue());
			healthContributors.put(externalServer.getKey(), healthIndicator);
		}
	}

	@Override
	public HealthContributor getContributor(final String name) {
		return healthContributors.get(name);
	}

	@Override
	public Stream<HealthContributors.Entry> stream() {
		return healthContributors.entrySet().stream()
		                         .map(entry -> new HealthContributors.Entry(entry.getKey(), entry.getValue()));
	}
}
