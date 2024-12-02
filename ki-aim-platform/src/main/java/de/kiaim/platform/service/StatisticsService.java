package de.kiaim.platform.service;

import de.kiaim.platform.exception.InternalIOException;
import de.kiaim.platform.model.entity.LobWrapperEntity;
import de.kiaim.platform.model.entity.ProjectEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	private final ProjectService projectService;

	public StatisticsService(
			@Value("classpath:organized_resemblance_metrics_2.yaml") final Resource statisticsResource,
			ProjectService projectService) {
		this.statisticsResource = statisticsResource;
		this.projectService = projectService;
	}

	/**
	 * Returns the statistics.
	 * @return The Statistics.
	 */
	@Transactional
	public String getStatistics(final ProjectEntity project) throws InternalIOException {
		final var rawData = project.getOriginalData().getStatistics();
		String data;

		if (rawData == null) {
			try {
				data = statisticsResource.getContentAsString(StandardCharsets.UTF_8);
				project.getOriginalData().setStatistics(new LobWrapperEntity(data));
				projectService.saveProject(project);
			} catch (IOException e) {
				throw new InternalIOException("N/A", "Failed to load statistics file!", e);
			}
		} else {
			data = rawData.getLobString();
		}

		return data;
	}
}
