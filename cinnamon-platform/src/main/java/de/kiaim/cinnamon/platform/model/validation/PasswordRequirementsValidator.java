package de.kiaim.cinnamon.platform.model.validation;

import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.enumeration.PasswordConstraints;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Validates if a string mets the configured password requirements.
 *
 * @author Daniel Preciado-Marquez
 */
public class PasswordRequirementsValidator implements ConstraintValidator<PasswordRequirements, String> {

	@Autowired
	private CinnamonConfiguration cinnamonConfiguration;

	@Override
	public boolean isValid(String value, final ConstraintValidatorContext context) {
		if (value == null || value.isBlank()) {
			customMessageForValidation(context, "Password must not be blank!");
			return false;
		}

		value = value.trim();

		final var minLength = cinnamonConfiguration.getPasswordRequirements().getMinLength();
		if (value.length() < cinnamonConfiguration.getPasswordRequirements().getMinLength()) {
			customMessageForValidation(context, "Password must be at least " + minLength + " characters long!");
			return false;
		}

		var constraints = cinnamonConfiguration.getPasswordRequirements().getConstraints();
		boolean hasLowercase = !constraints.contains(PasswordConstraints.LOWERCASE);
		boolean hasNumber = !constraints.contains(PasswordConstraints.DIGIT);
		boolean hasSpecialChar = !constraints.contains(PasswordConstraints.SPECIAL_CHAR);
		boolean hasUppercase = !constraints.contains(PasswordConstraints.UPPERCASE);

		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);

			if (Character.isDigit(c)) {
				hasNumber = true;
			} else if (Character.isLowerCase(c)) {
				hasLowercase = true;
			} else if (Character.isUpperCase(c)) {
				hasUppercase = true;
			} else {
				hasSpecialChar = true;
			}
		}

		if (!hasLowercase) {
			customMessageForValidation(context, "Password must contain at least one lowercase character!");
		}
		if (!hasNumber) {
			customMessageForValidation(context, "Password must contain at least one digit!");
		}
		if (!hasUppercase) {
			customMessageForValidation(context, "Password must contain at least one uppercase character!");
		}
		if (!hasSpecialChar) {
			customMessageForValidation(context, "Password must contain at least one special character!");
		}

		return hasLowercase && hasNumber && hasSpecialChar && hasUppercase;
	}

	private void customMessageForValidation(final ConstraintValidatorContext context, final  String message) {
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
	}
}
