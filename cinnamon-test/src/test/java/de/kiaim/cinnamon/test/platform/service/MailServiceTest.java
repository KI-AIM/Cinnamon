package de.kiaim.cinnamon.test.platform.service;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import de.kiaim.cinnamon.platform.exception.InternalMailException;
import de.kiaim.cinnamon.platform.model.entity.admin.EmailSettingsEntity;
import de.kiaim.cinnamon.platform.repository.EmailSettingsRepository;
import de.kiaim.cinnamon.platform.service.MailService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.TestSocketUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MailServiceTest {

	private static final String USERNAME = "mailer";
	private static final String PASSWORD = "password123";

	private MailService mailService;

	private GreenMail greenMail;
	private int port;

	@BeforeEach
	public void setup() {
		EmailSettingsRepository repository = mock(EmailSettingsRepository.class);
		when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(createSettings(false)));

		mailService = new MailService(repository);

		port = TestSocketUtils.findAvailableTcpPort();
		greenMail = new GreenMail(new ServerSetup(port, null, ServerSetup.PROTOCOL_SMTP));
		greenMail.setUser(USERNAME, PASSWORD);
		greenMail.start();
	}

	@AfterEach
	public void teardown() {
		greenMail.stop();
	}

	@Test
	public void sendMail() throws Exception {
		final EmailSettingsEntity settings = createSettings(false);

		mailService.sendMail(settings, "recipient@example.com", "Test subject", "Test body");

		assertTrue(greenMail.waitForIncomingEmail(5_000, 1));
		final MimeMessage[] messages = greenMail.getReceivedMessages();
		assertEquals(1, messages.length);

		final MimeMessage message = messages[0];
		assertEquals("Test subject", message.getSubject());
		assertEquals("Test body", GreenMailUtil.getBody(message).trim());
		assertEquals(settings.getMailSender(), message.getFrom()[0].toString());
		assertEquals("recipient@example.com", message.getAllRecipients()[0].toString());
	}

	@Test
	public void sendMailWithAuthentication() throws Exception {
		final EmailSettingsEntity settings = createSettings(true);

		mailService.sendMail(settings, "recipient@example.com", "Authenticated subject", "Authenticated body");

		assertTrue(greenMail.waitForIncomingEmail(5_000, 1));
		final MimeMessage[] messages = greenMail.getReceivedMessages();
		assertEquals(1, messages.length);
		assertEquals("Authenticated subject", messages[0].getSubject());
	}

	@Test
	public void sendMailFailsWithWrongCredentials() {
		final EmailSettingsEntity settings = createSettings(true);
		settings.setMailPassword("wrong-password");

		assertThrows(InternalMailException.class,
		            () -> mailService.sendMail(settings, "recipient@example.com", "Subject", "Body"));
	}

	@Test
	public void sendMailFailsWhenServerUnreachable() {
		final EmailSettingsEntity settings = createSettings(false);
		greenMail.stop();

		assertThrows(InternalMailException.class,
		            () -> mailService.sendMail(settings, "recipient@example.com", "Subject", "Body"));
	}

	private EmailSettingsEntity createSettings(final boolean withAuth) {
		final EmailSettingsEntity settings = new EmailSettingsEntity();
		settings.setMailHost("localhost");
		settings.setMailPort(port);
		settings.setMailTLS(false);
		settings.setMailSMTPAuth(withAuth);
		settings.setMailUsername(USERNAME);
		settings.setMailPassword(PASSWORD);
		settings.setMailSender("sender@example.com");
		return settings;
	}

}
