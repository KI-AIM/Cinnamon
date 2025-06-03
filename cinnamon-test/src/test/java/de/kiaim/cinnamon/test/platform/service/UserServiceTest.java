package de.kiaim.cinnamon.test.platform.service;

import de.kiaim.cinnamon.platform.exception.BadUserConfirmationException;
import de.kiaim.cinnamon.platform.model.dto.ConfirmUserRequest;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.service.UserService;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

public class UserServiceTest extends ContextRequiredTest {

	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private UserService userService;

	@Test
	public void confirmUser() {
		var email = "test_user";
		var password = "password";
		var request = new ConfirmUserRequest(email, password);

		var passwordEncoded = passwordEncoder.encode(password);

		var user = new UserEntity();
		user.setEmail(email);
		user.setPassword(passwordEncoded);

		assertDoesNotThrow(() -> userService.confirmUser(request, user));
	}

	@Test
	public void confirmUserInvalidUsername() {
		var email = "test_user";
		var emailInvalid = "invalid_user";
		var password = "password";
		var request = new ConfirmUserRequest(emailInvalid, password);

		var passwordEncoded = passwordEncoder.encode(password);

		var user = new UserEntity();
		user.setEmail(email);
		user.setPassword(passwordEncoded);

		var e = assertThrows(BadUserConfirmationException.class, () -> userService.confirmUser(request, user));
		assertEquals("PLATFORM_1_12_1", e.getErrorCode());
	}

	@Test
	public void confirmUserInvalidPassword() {
		var email = "test_user";
		var password = "password";
		var passwordInvalid = "invalid_password";
		var request = new ConfirmUserRequest(email, passwordInvalid);

		var passwordEncoded = passwordEncoder.encode(password);

		var user = new UserEntity();
		user.setEmail(email);
		user.setPassword(passwordEncoded);

		var e = assertThrows(BadUserConfirmationException.class, () -> userService.confirmUser(request, user));
		assertEquals("PLATFORM_1_12_2", e.getErrorCode());
	}

}
