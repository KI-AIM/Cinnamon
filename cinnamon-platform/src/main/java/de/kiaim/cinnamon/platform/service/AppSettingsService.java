package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.platform.exception.BadMailSettingsException;
import de.kiaim.cinnamon.platform.exception.InternalMailException;
import de.kiaim.cinnamon.platform.model.dto.EMailSettingsDTO;
import de.kiaim.cinnamon.platform.model.entity.admin.EmailSettingsEntity;
import de.kiaim.cinnamon.platform.model.mapper.MailSettingsMapper;
import de.kiaim.cinnamon.platform.repository.EmailSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing application wide settings.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class AppSettingsService {

	private static final String TEST_MAIL_SUBJECT = "Cinnamon test mail";
	private static final String TEST_MAIL_BODY =
			"This is a test mail sent by Cinnamon to verify the configured mail settings.";

	private final EmailSettingsRepository emailSettingsRepository;
	private final MailSettingsMapper mailSettingsMapper;
	private final MailService mailService;

	@Autowired
	public AppSettingsService(final EmailSettingsRepository emailSettingsRepository,
	                          final MailSettingsMapper mailSettingsMapper, final MailService mailService) {
		this.emailSettingsRepository = emailSettingsRepository;
		this.mailSettingsMapper = mailSettingsMapper;
		this.mailService = mailService;
	}

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

}
