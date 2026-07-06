package de.kiaim.cinnamon.platform.model.validation;

import de.kiaim.cinnamon.platform.model.dto.MatchingPasswords;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordMatchesValidator implements ConstraintValidator<PasswordMatches, MatchingPasswords> {

	@Override
	public boolean isValid(MatchingPasswords request, ConstraintValidatorContext context) {
		if (!request.getPassword().equals(request.getPasswordRepeated())) {
			context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
			       .addPropertyNode(request.getPasswordRepeatedFieldName()).addConstraintViolation();
			return false;
		}

		return true;
	}
}
