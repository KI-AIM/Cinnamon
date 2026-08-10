package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.platform.exception.BadEmailTemplateException;
import de.kiaim.cinnamon.platform.model.dto.EmailTemplateDTO;
import de.kiaim.cinnamon.platform.model.dto.EmailTemplateListDTO;
import de.kiaim.cinnamon.platform.model.entity.admin.EmailTemplateEntity;
import de.kiaim.cinnamon.platform.model.enumeration.SupportedLanguage;
import de.kiaim.cinnamon.platform.model.mapper.EmailTemplateMapper;
import de.kiaim.cinnamon.platform.repository.EmailTemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * Service for managing the email templates of the application.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class EmailTemplateService {

	private final EmailTemplateRepository emailTemplateRepository;
	private final EmailTemplateMapper emailTemplateMapper;

	@Autowired
	public EmailTemplateService(final EmailTemplateRepository emailTemplateRepository,
	                            final EmailTemplateMapper emailTemplateMapper) {
		this.emailTemplateRepository = emailTemplateRepository;
		this.emailTemplateMapper = emailTemplateMapper;
	}

	/**
	 * Returns all email templates together with all supported languages.
	 *
	 * @return The templates and the supported languages.
	 */
	@Transactional(readOnly = true)
	public EmailTemplateListDTO getEmailTemplates() {
		final List<EmailTemplateDTO> templates = emailTemplateRepository.findAllByOrderByNameAsc().stream()
		                                                                .map(emailTemplateMapper::toDto)
		                                                                .toList();

		final EmailTemplateListDTO result = new EmailTemplateListDTO();
		result.setLanguages(Arrays.stream(SupportedLanguage.values()).map(emailTemplateMapper::toDto).toList());
		result.setTemplates(templates);
		return result;
	}

	/**
	 * Returns the email template with the given ID.
	 *
	 * @param id The ID of the template.
	 * @return The template.
	 * @throws BadEmailTemplateException If no template with the given ID exists.
	 */
	@Transactional(readOnly = true)
	public EmailTemplateDTO getEmailTemplate(final Long id) throws BadEmailTemplateException {
		return emailTemplateMapper.toDto(getEmailTemplateEntity(id));
	}

	/**
	 * Creates a new email template based on the given DTO.
	 *
	 * @param emailTemplateDTO The template to create.
	 * @return The created template.
	 * @throws BadEmailTemplateException If another template with the same name already exists.
	 */
	@Transactional
	public EmailTemplateDTO createEmailTemplate(final EmailTemplateDTO emailTemplateDTO)
			throws BadEmailTemplateException {
		validateNameIsAvailable(emailTemplateDTO.getName(), null);

		final EmailTemplateEntity template = new EmailTemplateEntity();
		emailTemplateMapper.updateEntity(template, emailTemplateDTO);

		return emailTemplateMapper.toDto(emailTemplateRepository.save(template));
	}

	/**
	 * Updates the email template with the given ID based on the given DTO.
	 * The DTO contains the complete content of the template, so languages that are not part of the DTO are removed.
	 *
	 * @param id               The ID of the template to update.
	 * @param emailTemplateDTO The new values of the template.
	 * @return The updated template.
	 * @throws BadEmailTemplateException If no template with the given ID exists or if another template with the same
	 *                                   name already exists.
	 */
	@Transactional
	public EmailTemplateDTO updateEmailTemplate(final Long id, final EmailTemplateDTO emailTemplateDTO)
			throws BadEmailTemplateException {
		final EmailTemplateEntity template = getEmailTemplateEntity(id);
		validateNameIsAvailable(emailTemplateDTO.getName(), id);

		emailTemplateMapper.updateEntity(template, emailTemplateDTO);

		return emailTemplateMapper.toDto(emailTemplateRepository.save(template));
	}

	/**
	 * Deletes the email template with the given ID.
	 *
	 * @param id The ID of the template to delete.
	 * @throws BadEmailTemplateException If no template with the given ID exists.
	 */
	@Transactional
	public void deleteEmailTemplate(final Long id) throws BadEmailTemplateException {
		emailTemplateRepository.delete(getEmailTemplateEntity(id));
	}

	/**
	 * Returns the email template entity with the given ID.
	 *
	 * @param id The ID of the template.
	 * @return The template.
	 * @throws BadEmailTemplateException If no template with the given ID exists.
	 */
	private EmailTemplateEntity getEmailTemplateEntity(final Long id) throws BadEmailTemplateException {
		return emailTemplateRepository.findById(id)
		                              .orElseThrow(() -> BadEmailTemplateException.notFound(id));
	}

	/**
	 * Validates that the given name is not used by another template.
	 * The name is unique because it is used to reference a template.
	 *
	 * @param name The requested name.
	 * @param id   The ID of the template that should get the name or null if the template does not exist yet.
	 * @throws BadEmailTemplateException If another template with the given name already exists.
	 */
	private void validateNameIsAvailable(final String name, final Long id) throws BadEmailTemplateException {
		final var conflictingTemplate = emailTemplateRepository.findByName(name);

		if (conflictingTemplate.isPresent() && !conflictingTemplate.get().getId().equals(id)) {
			throw BadEmailTemplateException.nameExists(name);
		}
	}

}
