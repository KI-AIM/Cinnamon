package de.kiaim.cinnamon.test.platform.service;

import de.kiaim.cinnamon.platform.exception.BadMailSettingsException;
import de.kiaim.cinnamon.platform.model.dto.EMailSettingsDTO;
import de.kiaim.cinnamon.platform.repository.EmailSettingsRepository;
import de.kiaim.cinnamon.platform.service.AppSettingsService;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
public class AppSettingsServiceTest extends ContextRequiredTest {

	@Autowired private AppSettingsService appSettingsService;
	@Autowired private EmailSettingsRepository emailSettingsRepository;

	@Test
	public void getMailSettingsNotConfigured() {
		final var e = assertThrows(BadMailSettingsException.class, () -> appSettingsService.getMailSettings());
		assertEquals("PLATFORM_1_19_1", e.getErrorCode());
	}

	@Test
	public void setMailSettingsCreatesSettings() throws BadMailSettingsException {
		final EMailSettingsDTO settings = appSettingsService.setMailSettings(createRequest("mail.example.com"));

		assertEquals("mail.example.com", settings.getMailHost());
		assertEquals(587, settings.getMailPort());
		assertTrue(settings.isMailTLS());
		assertTrue(settings.isMailSMTPAuth());
		assertEquals("mailer", settings.getMailUsername());
		assertNull(settings.getMailPassword(), "The password should never be part of the returned settings!");
		assertTrue(settings.isMailPasswordSet());
		assertEquals("no-reply@example.com", settings.getMailSender());

		final EMailSettingsDTO persisted = appSettingsService.getMailSettings();
		assertEquals("mail.example.com", persisted.getMailHost());
		assertNull(persisted.getMailPassword(), "The password should never be part of the returned settings!");
		assertTrue(persisted.isMailPasswordSet());
	}

	@Test
	public void setMailSettingsWithoutPassword() {
		final EMailSettingsDTO request = createRequest("mail.example.com");
		request.setMailPassword(null);

		final EMailSettingsDTO settings = appSettingsService.setMailSettings(request);

		assertFalse(settings.isMailPasswordSet());
	}

	@Test
	public void setMailSettingsOverwritesExistingSettings() {
		appSettingsService.setMailSettings(createRequest("mail.example.com"));
		final EMailSettingsDTO second = appSettingsService.setMailSettings(createRequest("mail2.example.com"));

		assertEquals("mail2.example.com", second.getMailHost());
		assertEquals(1, emailSettingsRepository.count(), "There should only ever be a single row of mail settings!");
	}

	private EMailSettingsDTO createRequest(final String host) {
		final EMailSettingsDTO request = new EMailSettingsDTO();
		request.setMailHost(host);
		request.setMailPort(587);
		request.setMailTLS(true);
		request.setMailSMTPAuth(true);
		request.setMailUsername("mailer");
		request.setMailPassword("changeme");
		request.setMailSender("no-reply@example.com");
		return request;
	}

}
