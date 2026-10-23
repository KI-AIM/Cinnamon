package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.platform.exception.BadMailSettingsException;
import de.kiaim.cinnamon.platform.exception.InternalInvalidStateException;
import de.kiaim.cinnamon.platform.exception.InternalMailException;
import de.kiaim.cinnamon.platform.model.dto.EMailSettingsDTO;
import de.kiaim.cinnamon.platform.model.entity.admin.AppSettingsEntity;
import de.kiaim.cinnamon.platform.model.entity.admin.EmailSettingsEntity;
import de.kiaim.cinnamon.platform.model.mapper.MailSettingsMapper;
import de.kiaim.cinnamon.platform.repository.AppSettingsRepository;
import de.kiaim.cinnamon.platform.repository.EmailSettingsRepository;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing application wide settings.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
@Log4j2
public class AppSettingsService {

	private static final String TEST_MAIL_SUBJECT = "Cinnamon test mail";
	private static final String TEST_MAIL_BODY =
			"This is a test mail sent by Cinnamon to verify the configured mail settings.";

	private final AppSettingsRepository appSettingsRepository;
	private final EmailSettingsRepository emailSettingsRepository;
	private final MailSettingsMapper mailSettingsMapper;
	private final MailService mailService;

	@Autowired
	public AppSettingsService(
			final AppSettingsRepository appSettingsRepository,
			final EmailSettingsRepository emailSettingsRepository,
			final MailSettingsMapper mailSettingsMapper,
			final MailService mailService) {
		this.appSettingsRepository = appSettingsRepository;
		this.emailSettingsRepository = emailSettingsRepository;
		this.mailSettingsMapper = mailSettingsMapper;
		this.mailService = mailService;
	}

	// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ Mail settings ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ //

	/**
	 * Returns the mail settings of the application.
	 *
	 * @return The mail settings.
	 * @throws BadMailSettingsException If the mail settings have not been configured yet.
	 */
	@Transactional(readOnly = true)
	public EMailSettingsDTO getMailSettings() throws BadMailSettingsException {
		return mailSettingsMapper.toDto(mailService.getConfiguredMailSettings());
	}

	/**
	 * Creates or updates the mail settings of the application based on the given DTO.
	 * Since only a single set of mail settings exists, an existing configuration is overwritten.
	 *
	 * @param eMailSettingsDTO The new mail settings.
	 * @return The updated mail settings.
	 */
	@Transactional
	public EMailSettingsDTO setMailSettings(final EMailSettingsDTO eMailSettingsDTO) {
		final EmailSettingsEntity settings = emailSettingsRepository.findFirstByOrderByIdAsc()
		                                                            .orElseGet(EmailSettingsEntity::new);

		mailSettingsMapper.updateEntity(settings, eMailSettingsDTO);

		return mailSettingsMapper.toDto(emailSettingsRepository.save(settings));
	}

	/**
	 * Sends a test mail to the given address using the configured mail settings.
	 *
	 * @param mailAddress The address the test mail is sent to.
	 * @throws BadMailSettingsException If the mail settings have not been configured yet.
	 * @throws InternalMailException    If sending the test mail failed.
	 */
	@Transactional(readOnly = true)
	public void sendTestMail(final String mailAddress) throws BadMailSettingsException, InternalMailException {
		mailService.sendMail(mailAddress, TEST_MAIL_SUBJECT, TEST_MAIL_BODY);
	}

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ App settings ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ //

	/**
	 * Initializes the app settings.
	 * If there is no app settings entity in the database, creates a new one with default settings.
	 * Otherwise, does nothing.
	 */
	@Transactional
	public void initAppSettings() {
		final var settingsCount = appSettingsRepository.count();

		if (settingsCount >= 1) {
			log.info("App settings already exist. Skipping creation.");
			return;
		}

		final var appSettings = new AppSettingsEntity();
		appSettingsRepository.save(appSettings);
		log.info("Initialized app settings with default values.");
	}

	/**
	 * Returns the imprint from the current app settings.
	 *
	 * @return The imprint.
	 * @throws InternalInvalidStateException If no app settings are stored in the database.
	 */
	@Transactional(readOnly = true)
	public @Nullable String getImprint() throws InternalInvalidStateException {
		return getAppSettings().getImprint();
	}

	/**
	 * Updates the imprint in the app settings.
	 *
	 * @param newImprint The new imprint to be set.
	 * @throws InternalInvalidStateException If no app settings are stored in the database.
	 */
	@Transactional
	public void updateImprint(@Nullable final String newImprint) throws InternalInvalidStateException {
		final var appSettings = getAppSettings();
		appSettings.setImprint(newImprint);
		log.debug("Updated imprint.");
	}

	/**
	 * Returns the AppSettingsEntity from the database.
	 *
	 * @return The app settings.
	 * @throws InternalInvalidStateException If no app settings are stored in the database.
	 */
	@Transactional(readOnly = true)
	public AppSettingsEntity getAppSettings() throws InternalInvalidStateException {
		final var appSettings = appSettingsRepository.findFirstBy();

		if (appSettings.isEmpty()) {
			throw new InternalInvalidStateException(InternalInvalidStateException.MISSING_APP_SETTINGS,
			                                        "Tried to access app settings, but none exist!");
		}

		return appSettings.get();
	}

}
