package de.kiaim.platform.service;

import de.kiaim.platform.exception.InternalIOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Service for statistics.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class StatisticsService {

	private final Resource statisticsResource;

	public StatisticsService(
			@Value("classpath:organized_resemblance_metrics.yaml") final Resource statisticsResource
	) {
		this.statisticsResource = statisticsResource;
	}

	/**
	 * Returns the statistics.
	 * @return The Statistics.
	 */
	public String getStatistics() throws InternalIOException {
		try {
			return statisticsResource.getContentAsString(StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new InternalIOException("N/A", "Failed to load statistics file!", e);
		}
	}
}
