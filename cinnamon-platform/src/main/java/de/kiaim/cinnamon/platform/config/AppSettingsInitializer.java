package de.kiaim.cinnamon.platform.config;

import de.kiaim.cinnamon.platform.model.entity.admin.AppSettingsEntity;
import de.kiaim.cinnamon.platform.repository.AppSettingsRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Initializes the app settings on start up.
 * Creates a new AppSettingsEntity if none exists in the database.
 *
 * @author Daniel Preciado-Marquez
 */
@Component
@Log4j2
public class AppSettingsInitializer implements ApplicationRunner {

	private final AppSettingsRepository appSettingsRepository;

	public AppSettingsInitializer(final AppSettingsRepository appSettingsRepository) {
		this.appSettingsRepository = appSettingsRepository;
	}

	@Override
	public void run(final ApplicationArguments args) throws Exception {
		final var settingsCount = appSettingsRepository.count();

		if (settingsCount == 0) {
			final var appSettings = new AppSettingsEntity();
			appSettingsRepository.save(appSettings);
			log.info("Initialized app settings with default values.");
		} else {
			log.info("App settings already exist. Skipping creation.");
		}

	}
}
