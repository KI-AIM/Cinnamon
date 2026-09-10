package de.kiaim.cinnamon.platform.model.validation;

import de.kiaim.cinnamon.platform.service.UserService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;

public class UsernameAvailableValidator implements ConstraintValidator<UsernameAvailable, String> {

	@Autowired
	UserService userService;

	@Override
	public boolean isValid(@Nullable String username, ConstraintValidatorContext context) {
		if (username == null) {
			return true; // Consider null as valid, use @NotNull for null check
		}

		return !userService.doesUserWithUsernameExist(username);
	}
}
