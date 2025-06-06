package de.kiaim.cinnamon.platform.model.validation;

import de.kiaim.cinnamon.platform.model.dto.RegisterRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordMatchesValidator implements ConstraintValidator<PasswordMatches, RegisterRequest> {

	@Override
	public boolean isValid(RegisterRequest request, ConstraintValidatorContext context) {
		if (!request.getPassword().equals(request.getPasswordRepeated())) {
			context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
			       .addPropertyNode("passwordRepeated").addConstraintViolation();
			return false;
		}

		return true;
	}
}
