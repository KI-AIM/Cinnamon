package de.kiaim.cinnamon.platform.model.validation;

import de.kiaim.cinnamon.platform.model.dto.UserInvitationRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that the request specifies a valid email.
 * Either a template or a subject and body must be specified.
 * Can be used with {@link EmailContent} annotation.
 *
 * @author Daniel Preciado-Marquez
 */
public class EmailContentValidator implements ConstraintValidator<EmailContent, UserInvitationRequest> {

	@Override
	public boolean isValid(UserInvitationRequest value, ConstraintValidatorContext context) {
		if (value == null) {
			return true; // Consider null as valid, use @NotNull for null check
		}

		if (value.getEmailTemplateItem() != null) {
			return true;
		}

		return value.getEmailCustomSubject() != null && value.getEmailCustomBody() != null;
	}
}
