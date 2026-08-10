package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.platform.model.validation.SmtpAuthCredentials;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * The mail settings of the application.
 * Used both for retrieving and setting the mail settings.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "The mail settings of the application.")
@SmtpAuthCredentials
@Getter @Setter
public class EMailSettingsDTO {

	@Schema(description = "Host of the application mailer.", example = "smtp.example.com")
	@NotBlank(message = "Host must not be blank.")
	private String mailHost;

	@Schema(description = "Port of the application mailer.", example = "587")
	@Min(value = 1, message = "Port must be between 1 and 65535.")
	@Max(value = 65535, message = "Port must be between 1 and 65535.")
	private int mailPort;

	@Schema(description = "Whether TLS is enabled.")
	private boolean mailTLS;

	@Schema(description = "Whether SMTP authentication is enabled.")
	private boolean mailSMTPAuth;

	@Schema(description = "Username of the application mailer. Only required if SMTP authentication is enabled.",
	        example = "mailer")
	private String mailUsername;

	@Schema(description = "Password of the application mailer. Only used when setting the mail settings; "
	                      + "never part of a response. Only used if SMTP authentication is enabled.",
	        example = "changeme")
	private String mailPassword;

	@Schema(description = "Whether a password has been configured for the application mailer. Only part of "
	                      + "a response, ignored when setting the mail settings.",
	        accessMode = Schema.AccessMode.READ_ONLY)
	private boolean mailPasswordSet;

	@Schema(description = "Sender of the emails sent from the application mailer.", example = "no-reply@example.com")
	@NotBlank(message = "Sender must not be blank.")
	@Email(message = "Sender must be a valid email address.")
	private String mailSender;

}
