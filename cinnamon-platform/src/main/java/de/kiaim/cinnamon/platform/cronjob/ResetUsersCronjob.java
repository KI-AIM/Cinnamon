package de.kiaim.cinnamon.platform.cronjob;

import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.service.UserService;
import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "cinnamon.is-demo-instance", havingValue = "true")
@Log4j2
public class ResetUsersCronjob {

	private final boolean isDemoInstance;
	private final CinnamonConfiguration cinnamonConfiguration;
	private final UserService userService;

	public ResetUsersCronjob(
			@Value("${cinnamon.is-demo-instance}") final boolean isDemoInstance,
			final CinnamonConfiguration cinnamonConfiguration,
			final UserService userService) {
		this.isDemoInstance = isDemoInstance;
		this.cinnamonConfiguration = cinnamonConfiguration;
		this.userService = userService;
	}

	@Scheduled(cron = "0 0 2 * * ?")
	public void resetUsers() {
		final var deletedRoles = cinnamonConfiguration.getDemoInstanceDeletedRoles();
		log.info("Resetting users with roles {}...", deletedRoles);

		try {
			this.userService.deleteUsersWithRoles(deletedRoles);
			log.info("Finished resetting users.");
		} catch (final Exception e) {
			log.error("Error resetting users", e);
		}
	}

	@PostConstruct
	public void logStatus() {
		if (isDemoInstance) {
			log.info("Running demo instance. Cronjob for resetting users with roles {} is enabled.",
			         cinnamonConfiguration.getDemoInstanceDeletedRoles());
		}
	}

}
