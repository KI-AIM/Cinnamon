package de.kiaim.cinnamon.platform.cronjob;

import de.kiaim.cinnamon.platform.service.UserInvitationService;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cronjob that deletes accepted or revoked invitations after the configured retention duration.
 *
 * @author Daniel Preciado-Marquez
 */
@Component
@Log4j2
public class DeleteExpiredInvitations {

	private final UserInvitationService userInvitationService;

	public DeleteExpiredInvitations(final UserInvitationService userInvitationService) {
		this.userInvitationService = userInvitationService;
	}

	@Scheduled(cron = "0 10 2 * * ?")
	public void deleteExpiredInvitations() {
		log.info("Deleting expired invitations...");

		try {
			userInvitationService.deleteExpiredInvitations();
			log.info("Finished deleting expired invitations.");
		} catch (final Exception e) {
			log.error("Error deleting expired invitations", e);
		}
	}
}
