package de.kiaim.platform.model.validation;

import de.kiaim.platform.service.UserService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

public class EmailAvailableValidator implements ConstraintValidator<EmailAvailable, String> {

	@Autowired
	UserService userService;

	@Override
	public boolean isValid(String email, ConstraintValidatorContext context) {
		return !userService.doesUserWithEmailExist(email);
	}
}
