package de.kiaim.cinnamon.test.platform.service;

import com.icegreen.greenmail.util.GreenMail;
import de.kiaim.cinnamon.platform.exception.BadMailSettingsException;
import de.kiaim.cinnamon.platform.exception.InternalMailException;
import de.kiaim.cinnamon.platform.model.dto.EMailSettingsDTO;
import de.kiaim.cinnamon.platform.repository.EmailSettingsRepository;
import de.kiaim.cinnamon.platform.service.AppSettingsService;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import de.kiaim.cinnamon.test.util.GreenMailPort;
import de.kiaim.cinnamon.test.util.WithGreenMail;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
@WithGreenMail
public class AppSettingsServiceTest extends ContextRequiredTest {

	@Autowired private AppSettingsService appSettingsService;
	@Autowired private EmailSettingsRepository emailSettingsRepository;

	private GreenMail greenMail;
	@GreenMailPort private int port;

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
	public void setMailSettingsKeepsExistingPassword() {
		appSettingsService.setMailSettings(createRequest("mail.example.com"));

		final EMailSettingsDTO request = createRequest("mail2.example.com");
		request.setMailPassword(null);
		final EMailSettingsDTO updated = appSettingsService.setMailSettings(request);

		assertEquals("mail2.example.com", updated.getMailHost());
		assertTrue(updated.isMailPasswordSet(), "The stored password should be kept if none is given!");
		assertEquals("changeme", emailSettingsRepository.findFirstByOrderByIdAsc().orElseThrow().getMailPassword());
	}

	@Test
	public void setMailSettingsOverwritesExistingSettings() {
		appSettingsService.setMailSettings(createRequest("mail.example.com"));
		final EMailSettingsDTO second = appSettingsService.setMailSettings(createRequest("mail2.example.com"));

		assertEquals("mail2.example.com", second.getMailHost());
		assertEquals(1, emailSettingsRepository.count(), "There should only ever be a single row of mail settings!");
	}

	@Test
	public void sendTestMailNotConfigured() {
		final var e = assertThrows(BadMailSettingsException.class, () -> appSettingsService.sendTestMail(
				"recipient@example.com"));
		assertEquals("PLATFORM_1_19_1", e.getErrorCode());
	}

	@Test
	public void sendTestMailSendsMail() throws Exception {
		appSettingsService.setMailSettings(createGreenMailRequest());

		appSettingsService.sendTestMail("recipient@example.com");

		assertTrue(greenMail.waitForIncomingEmail(5_000, 1));
		final MimeMessage[] messages = greenMail.getReceivedMessages();
		assertEquals(1, messages.length);
		assertEquals("Cinnamon test mail", messages[0].getSubject());
		assertEquals("no-reply@example.com", messages[0].getFrom()[0].toString());
		assertEquals("recipient@example.com", messages[0].getAllRecipients()[0].toString());
	}

	@Test
	public void sendTestMailFailsWhenServerUnreachable() {
		appSettingsService.setMailSettings(createGreenMailRequest());
		greenMail.stop();

		assertThrows(InternalMailException.class, () -> appSettingsService.sendTestMail("recipient@example.com"));
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

	/**
	 * Settings pointing at the embedded GreenMail test server, used to actually verify mails are sent.
	 */
	private EMailSettingsDTO createGreenMailRequest() {
		final EMailSettingsDTO request = createRequest("localhost");
		request.setMailPort(port);
		request.setMailTLS(false);
		request.setMailSMTPAuth(false);
		return request;
	}

}
