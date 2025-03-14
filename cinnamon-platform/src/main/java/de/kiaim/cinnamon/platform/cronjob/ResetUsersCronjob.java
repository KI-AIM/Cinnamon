package de.kiaim.cinnamon.platform.cronjob;

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
	private final UserService userService;

	public ResetUsersCronjob(
			@Value("${cinnamon.is-demo-instance}") final boolean isDemoInstance,
			final UserService userService) {
		this.isDemoInstance = isDemoInstance;
		this.userService = userService;
	}

	@Scheduled(cron = "0 0 2 * * ?")
	public void resetUsers() {
		log.info("Resetting users...");

		try {
			this.userService.deleteAllUsers();
			log.info("Finished resetting users.");
		} catch (final Exception e) {
			log.error("Error resetting users", e);
		}
	}

	@PostConstruct
	public void logStatus() {
		if (isDemoInstance) {
			log.info("Running demo instance. Cronjob for resetting users is enabled.");
		}
	}

}
