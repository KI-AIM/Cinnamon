package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.platform.exception.BadMailSettingsException;
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

	private final EmailSettingsRepository emailSettingsRepository;
	private final MailSettingsMapper mailSettingsMapper;

	@Autowired
	public AppSettingsService(final EmailSettingsRepository emailSettingsRepository,
	                          final MailSettingsMapper mailSettingsMapper) {
		this.emailSettingsRepository = emailSettingsRepository;
		this.mailSettingsMapper = mailSettingsMapper;
	}

	/**
	 * Returns the mail settings of the application.
	 *
	 * @return The mail settings.
	 * @throws BadMailSettingsException If the mail settings have not been configured yet.
	 */
	@Transactional(readOnly = true)
	public EMailSettingsDTO getMailSettings() throws BadMailSettingsException {
		final EmailSettingsEntity settings = emailSettingsRepository.findFirstByOrderByIdAsc()
		                                                            .orElseThrow(() -> new BadMailSettingsException(
				                                                            BadMailSettingsException.NOT_FOUND,
				                                                            "Mail settings have not been configured yet!"));
		return mailSettingsMapper.toDto(settings);
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

}
