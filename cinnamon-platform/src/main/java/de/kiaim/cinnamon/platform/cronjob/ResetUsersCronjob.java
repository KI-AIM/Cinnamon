package de.kiaim.cinnamon.platform.cronjob;

import de.kiaim.cinnamon.platform.exception.BadDataSetIdException;
import de.kiaim.cinnamon.platform.exception.InternalDataSetPersistenceException;
import de.kiaim.cinnamon.platform.service.UserService;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "cinnamon.is-demo-instance", havingValue = "true")
@Log4j2
public class ResetUsersCronjob {

	private final UserService userService;

	public ResetUsersCronjob(final UserService userService) {
		this.userService = userService;
	}

	//@Scheduled(cron = "0 0 2 * * ?")
	@Scheduled(cron = "0 * * * * ?")
	public void resetUsers() {
		log.info("Resetting users...");

		try {
			this.userService.deleteAllUsers();
			log.info("Finished resetting users.");
		} catch (final Exception e) {
			log.error("Error resetting users", e);
		}
	}

}
