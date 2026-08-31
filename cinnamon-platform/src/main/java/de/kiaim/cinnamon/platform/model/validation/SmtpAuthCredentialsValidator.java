package de.kiaim.cinnamon.platform.model.validation;

import de.kiaim.cinnamon.platform.model.dto.EMailSettingsDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for {@link SmtpAuthCredentials}.
 * The username is only required if SMTP authentication is enabled since it is not used otherwise.
 *
 * @author Daniel Preciado-Marquez
 */
public class SmtpAuthCredentialsValidator implements ConstraintValidator<SmtpAuthCredentials, EMailSettingsDTO> {

	@Override
	public boolean isValid(final EMailSettingsDTO settings, final ConstraintValidatorContext context) {
		if (!settings.isMailSMTPAuth()) {
			return true;
		}

		if (settings.getMailUsername() != null && !settings.getMailUsername().isBlank()) {
			return true;
		}

		// Report the violation for the username instead of the whole request.
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
		       .addPropertyNode("mailUsername").addConstraintViolation();
		return false;
	}
}
