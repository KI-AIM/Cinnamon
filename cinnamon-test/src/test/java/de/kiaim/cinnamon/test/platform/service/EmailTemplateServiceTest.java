package de.kiaim.cinnamon.test.platform.service;

import de.kiaim.cinnamon.platform.exception.BadEmailTemplateException;
import de.kiaim.cinnamon.platform.model.dto.EmailTemplateDTO;
import de.kiaim.cinnamon.platform.model.dto.EmailTemplateItemDTO;
import de.kiaim.cinnamon.platform.model.dto.EmailTemplateListDTO;
import de.kiaim.cinnamon.platform.model.dto.SupportedLanguageDTO;
import de.kiaim.cinnamon.platform.model.enumeration.SupportedLanguage;
import de.kiaim.cinnamon.platform.repository.EmailTemplateRepository;
import de.kiaim.cinnamon.platform.service.EmailTemplateService;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EmailTemplateService}.
 *
 * @author Daniel Preciado-Marquez
 */
@Transactional
public class EmailTemplateServiceTest extends ContextRequiredTest {

	@Autowired private EmailTemplateService emailTemplateService;
	@Autowired private EmailTemplateRepository emailTemplateRepository;

	@Test
	public void getEmailTemplatesReturnsAllSupportedLanguages() {
		final EmailTemplateListDTO templates = emailTemplateService.getEmailTemplates();

		assertEquals(SupportedLanguage.values().length, templates.getLanguages().size());

		final SupportedLanguageDTO english = templates.getLanguages().stream()
		                                              .filter(language -> language.getName()
		                                                                          .equals(SupportedLanguage.ENGLISH.name()))
		                                              .findFirst()
		                                              .orElse(null);
		assertNotNull(english, "English should be supported!");
		assertEquals("English", english.getDisplayName());
	}

	@Test
	public void getEmailTemplatesReturnsTemplatesSortedByName() throws BadEmailTemplateException {
		emailTemplateService.createEmailTemplate(createRequest("Second", "Subject", "Body"));
		emailTemplateService.createEmailTemplate(createRequest("First", "Subject", "Body"));

		final List<EmailTemplateDTO> templates = emailTemplateService.getEmailTemplates().getTemplates();

		assertEquals(2, templates.size());
		assertEquals("First", templates.get(0).getName());
		assertEquals("Second", templates.get(1).getName());
	}

	@Test
	public void getEmailTemplateNotFound() {
		final var e = assertThrows(BadEmailTemplateException.class, () -> emailTemplateService.getEmailTemplate(42L));
		assertEquals("PLATFORM_1_20_1", e.getErrorCode());
	}

	@Test
	public void createEmailTemplateCreatesTemplate() throws BadEmailTemplateException {
		final EmailTemplateDTO created = emailTemplateService.createEmailTemplate(
				createRequest("Registration", "Welcome", "Hello!"));

		assertNotNull(created.getId());
		assertEquals("Registration", created.getName());
		assertEquals(1, created.getItems().size());
		assertEquals(SupportedLanguage.ENGLISH, created.getItems().get(0).getLanguage());
		assertEquals("Welcome", created.getItems().get(0).getSubject());
		assertEquals("Hello!", created.getItems().get(0).getBody());

		final EmailTemplateDTO persisted = emailTemplateService.getEmailTemplate(created.getId());
		assertEquals("Registration", persisted.getName());
		assertEquals(1, persisted.getItems().size());
	}

	@Test
	public void createEmailTemplateWithExistingName() throws BadEmailTemplateException {
		emailTemplateService.createEmailTemplate(createRequest("Registration", "Welcome", "Hello!"));

		final var e = assertThrows(BadEmailTemplateException.class, () -> emailTemplateService.createEmailTemplate(
				createRequest("Registration", "Other subject", "Other body")));
		assertEquals("PLATFORM_1_20_2", e.getErrorCode());
	}

	@Test
	public void updateEmailTemplateUpdatesTemplate() throws BadEmailTemplateException {
		final EmailTemplateDTO created = emailTemplateService.createEmailTemplate(
				createRequest("Registration", "Welcome", "Hello!"));
		final Long itemId = emailTemplateRepository.findById(created.getId()).orElseThrow()
		                                           .getItems().iterator().next().getId();

		final EmailTemplateDTO updated = emailTemplateService.updateEmailTemplate(
				created.getId(), createRequest("Confirmation", "Confirm your account", "Please confirm!"));

		assertEquals(created.getId(), updated.getId());
		assertEquals("Confirmation", updated.getName());
		assertEquals(1, updated.getItems().size());
		assertEquals("Confirm your account", updated.getItems().get(0).getSubject());
		assertEquals("Please confirm!", updated.getItems().get(0).getBody());

		final var items = emailTemplateRepository.findById(created.getId()).orElseThrow().getItems();
		assertEquals(1, items.size(), "The content of an already configured language should be updated!");
		assertEquals(itemId, items.iterator().next().getId(),
		             "The content of an already configured language should be updated!");
	}

	@Test
	public void updateEmailTemplateKeepsItsOwnName() throws BadEmailTemplateException {
		final EmailTemplateDTO created = emailTemplateService.createEmailTemplate(
				createRequest("Registration", "Welcome", "Hello!"));

		final EmailTemplateDTO updated = emailTemplateService.updateEmailTemplate(
				created.getId(), createRequest("Registration", "Welcome back", "Hello again!"));

		assertEquals("Registration", updated.getName());
	}

	@Test
	public void updateEmailTemplateWithNameOfAnotherTemplate() throws BadEmailTemplateException {
		emailTemplateService.createEmailTemplate(createRequest("Registration", "Welcome", "Hello!"));
		final EmailTemplateDTO other = emailTemplateService.createEmailTemplate(
				createRequest("Confirmation", "Confirm", "Please confirm!"));

		final var e = assertThrows(BadEmailTemplateException.class, () -> emailTemplateService.updateEmailTemplate(
				other.getId(), createRequest("Registration", "Confirm", "Please confirm!")));
		assertEquals("PLATFORM_1_20_2", e.getErrorCode());
	}

	@Test
	public void createEmailTemplateWithMultipleLanguages() throws BadEmailTemplateException {
		final EmailTemplateDTO request = createRequest("Registration", "Welcome", "Hello!");
		request.getItems().add(createItem(SupportedLanguage.GERMAN, "Willkommen", "Hallo!"));

		final EmailTemplateDTO created = emailTemplateService.createEmailTemplate(request);

		assertEquals(2, created.getItems().size());
		assertEquals(SupportedLanguage.ENGLISH, created.getItems().get(0).getLanguage(),
		             "The content should be sorted by language!");
		assertEquals(SupportedLanguage.GERMAN, created.getItems().get(1).getLanguage(),
		             "The content should be sorted by language!");
		assertEquals("Willkommen", created.getItems().get(1).getSubject());
		assertEquals("Hallo!", created.getItems().get(1).getBody());
	}

	/**
	 * The request always contains the complete content of a template, so a language that is missing is removed.
	 */
	@Test
	public void updateEmailTemplateRemovesMissingLanguages() throws BadEmailTemplateException {
		final EmailTemplateDTO createRequest = createRequest("Registration", "Welcome", "Hello!");
		createRequest.getItems().add(createItem(SupportedLanguage.GERMAN, "Willkommen", "Hallo!"));
		final EmailTemplateDTO created = emailTemplateService.createEmailTemplate(createRequest);

		final EmailTemplateDTO updateRequest = new EmailTemplateDTO();
		updateRequest.setName("Registration");
		updateRequest.getItems().add(createItem(SupportedLanguage.GERMAN, "Willkommen", "Hallo!"));

		final EmailTemplateDTO updated = emailTemplateService.updateEmailTemplate(created.getId(), updateRequest);

		assertEquals(1, updated.getItems().size());
		assertEquals(SupportedLanguage.GERMAN, updated.getItems().get(0).getLanguage());

		final var persistedItems = emailTemplateRepository.findById(created.getId()).orElseThrow().getItems();
		assertEquals(1, persistedItems.size(), "The removed content should be deleted!");
		assertEquals(SupportedLanguage.GERMAN, persistedItems.iterator().next().getLanguage());
	}

	@Test
	public void updateEmailTemplateNotFound() {
		final var e = assertThrows(BadEmailTemplateException.class, () -> emailTemplateService.updateEmailTemplate(
				42L, createRequest("Registration", "Welcome", "Hello!")));
		assertEquals("PLATFORM_1_20_1", e.getErrorCode());
	}

	@Test
	public void deleteEmailTemplateDeletesTemplate() throws BadEmailTemplateException {
		final EmailTemplateDTO created = emailTemplateService.createEmailTemplate(
				createRequest("Registration", "Welcome", "Hello!"));

		emailTemplateService.deleteEmailTemplate(created.getId());

		assertTrue(emailTemplateService.getEmailTemplates().getTemplates().isEmpty());
	}

	@Test
	public void deleteEmailTemplateNotFound() {
		final var e = assertThrows(BadEmailTemplateException.class,
		                           () -> emailTemplateService.deleteEmailTemplate(42L));
		assertEquals("PLATFORM_1_20_1", e.getErrorCode());
	}

	private EmailTemplateDTO createRequest(final String name, final String subject, final String body) {
		final EmailTemplateDTO request = new EmailTemplateDTO();
		request.setName(name);
		request.getItems().add(createItem(SupportedLanguage.ENGLISH, subject, body));
		return request;
	}

	private EmailTemplateItemDTO createItem(final SupportedLanguage language, final String subject,
	                                        final String body) {
		final EmailTemplateItemDTO item = new EmailTemplateItemDTO();
		item.setLanguage(language);
		item.setSubject(subject);
		item.setBody(body);
		return item;
	}

}
