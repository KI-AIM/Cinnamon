package de.kiaim.cinnamon.platform.cronjob;

import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.service.ProjectService;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cronjob that deletes expired projects.
 *
 * @author Daniel Preciado-Marquez
 */
@Component
@Log4j2
public class DeleteExpiredProjects {

	private final ProjectService projectService;

	public DeleteExpiredProjects(final ProjectService projectService) {
		this.projectService = projectService;
	}

	@Scheduled(cron = "0 5 2 * * ?")
	public void deleteExpiredProjects() {
		log.info("Deleting expired projects...");

		final List<ProjectEntity> expiredProjects = projectService.getExpiredProjects();
		for (final ProjectEntity project : expiredProjects) {
			try {
				projectService.deleteProject(project);
			} catch (final Exception e) {
				log.error("Error deleting expired project: {}", project.getExternalId(), e);
			}
		}
	}
}
