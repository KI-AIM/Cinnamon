package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.platform.exception.BadMailSettingsException;
import de.kiaim.cinnamon.platform.exception.InternalMailException;
import de.kiaim.cinnamon.platform.model.entity.admin.EmailSettingsEntity;
import de.kiaim.cinnamon.platform.repository.EmailSettingsRepository;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * Service for sending emails.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class MailService {

	private final EmailSettingsRepository emailSettingsRepository;

	public MailService(final EmailSettingsRepository emailSettingsRepository) {
		this.emailSettingsRepository = emailSettingsRepository;
	}

	/**
	 * Returns the configured mail settings entity.
	 *
	 * @return The mail settings.
	 * @throws BadMailSettingsException If the mail settings have not been configured yet.
	 */
	public EmailSettingsEntity getConfiguredMailSettings() throws BadMailSettingsException {
		return emailSettingsRepository.findFirstByOrderByIdAsc()
		                              .orElseThrow(
				                              () -> new BadMailSettingsException(BadMailSettingsException.NOT_FOUND,
				                                                                 "Mail settings have not been configured yet!"));
	}

	/**
	 * Sends an email with the given subject and body to the given recipient.
	 * The connection to the mail server is configured based on the currently stored mail settings.
	 *
	 * @param recipient The mail address of the recipient.
	 * @param subject   The subject of the email.
	 * @param body      The body of the email.
	 * @throws BadMailSettingsException If the mail settings have not been configured yet.
	 * @throws InternalMailException    If sending the email failed.
	 */
	public void sendMail(final String recipient, final String subject, final String body)
			throws BadMailSettingsException, InternalMailException {
		final EmailSettingsEntity settings = getConfiguredMailSettings();
		sendMail(settings, recipient, subject, body);
	}

	/**
	 * Sends an email with the given subject and body to the given recipient.
	 * The connection to the mail server is configured based on the given settings.
	 *
	 * @param settings  The settings of the application mailer.
	 * @param recipient The mail address of the recipient.
	 * @param subject   The subject of the email.
	 * @param body      The body of the email.
	 * @throws InternalMailException If sending the email failed.
	 */
	public void sendMail(final EmailSettingsEntity settings, final String recipient, final String subject,
	                     final String body) throws InternalMailException {
		final JavaMailSenderImpl mailSender = buildMailSender(settings);

		final SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(settings.getMailSender());
		message.setTo(recipient);
		message.setSubject(subject);
		message.setText(body);

		try {
			mailSender.send(message);
		} catch (final MailException e) {
			throw new InternalMailException(InternalMailException.SENDING,
			                                "Failed to send email to '" + recipient + "'!", e);
		}
	}

	/**
	 * Builds a mail sender configured with the given settings.
	 *
	 * @param settings The settings of the application mailer.
	 * @return The configured mail sender.
	 */
	private JavaMailSenderImpl buildMailSender(final EmailSettingsEntity settings) {
		final JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
		mailSender.setHost(settings.getMailHost());
		mailSender.setPort(settings.getMailPort());

		if (settings.isMailSMTPAuth()) {
			mailSender.setUsername(settings.getMailUsername());
			mailSender.setPassword(settings.getMailPassword());
		}

		final Properties mailProperties = mailSender.getJavaMailProperties();
		mailProperties.put("mail.transport.protocol", "smtp");
		mailProperties.put("mail.smtp.auth", settings.isMailSMTPAuth());
		mailProperties.put("mail.smtp.starttls.enable", settings.isMailTLS());

		return mailSender;
	}

}
