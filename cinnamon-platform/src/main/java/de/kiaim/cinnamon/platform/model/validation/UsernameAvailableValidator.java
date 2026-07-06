package de.kiaim.cinnamon.platform.model.validation;

import de.kiaim.cinnamon.platform.service.UserService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

public class UsernameAvailableValidator implements ConstraintValidator<UsernameAvailable, String> {

	@Autowired
	UserService userService;

	@Override
	public boolean isValid(String username, ConstraintValidatorContext context) {
		return !userService.doesUserWithUsernameExist(username);
	}
}
