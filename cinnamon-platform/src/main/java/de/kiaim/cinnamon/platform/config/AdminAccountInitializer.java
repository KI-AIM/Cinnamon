package de.kiaim.cinnamon.platform.config;

import de.kiaim.cinnamon.platform.model.configuration.AdminConfiguration;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.dto.RegisterRequest;
import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import de.kiaim.cinnamon.platform.service.UserService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates the initial administrator account configured under {@code cinnamon.admin} on startup.
 * Does nothing if no administrator has been configured or if the configured user already exists,
 * so a password changed after the first start is never overwritten.
 *
 * @author Daniel Preciado-Marquez
 */
@Component
@Log4j2
public class AdminAccountInitializer implements ApplicationRunner {

	private final CinnamonConfiguration cinnamonConfiguration;
	private final UserService userService;
	private final Validator validator;

	public AdminAccountInitializer(final CinnamonConfiguration cinnamonConfiguration, final UserService userService,
	                               final Validator validator) {
		this.cinnamonConfiguration = cinnamonConfiguration;
		this.userService = userService;
		this.validator = validator;
	}

	@Override
	public void run(final ApplicationArguments args) throws Exception {
		final AdminConfiguration admin = cinnamonConfiguration.getAdmin();

		if (!admin.isConfigured()) {
			log.info("No initial administrator configured. Set 'cinnamon.admin.username' and " +
			         "'cinnamon.admin.password' to create one.");
			return;
		}

		final String username = admin.getUsername().trim();

		if (userService.doesUserWithUsernameExist(username)) {
			log.info("Initial administrator '{}' already exists. Skipping creation.", username);
			return;
		}

		validatePassword(username, admin.getPassword());

		userService.register(username, admin.getPassword(), Set.of(UserRole.values()), null);
		log.info("Created initial administrator '{}'.", username);
	}

	/**
	 * Validates the configured password against the configured password requirements.
	 * The requirements are otherwise only enforced on the registration endpoint,
	 * which the initial administrator does not pass through.
	 *
	 * @param username The username of the initial administrator, used for the error message.
	 * @param password The configured password.
	 * @throws IllegalStateException If the password is not set or does not meet the requirements.
	 */
	private void validatePassword(final String username, final String password) {
		if (password == null || password.isBlank()) {
			throw new IllegalStateException(
					"The initial administrator '" + username + "' is configured without a password. " +
					"Set 'cinnamon.admin.password' or remove 'cinnamon.admin.username'.");
		}

		final var violations = validator.validateValue(RegisterRequest.class, "password", password);
		if (!violations.isEmpty()) {
			final String messages = violations.stream()
			                                  .map(ConstraintViolation::getMessage)
			                                  .collect(Collectors.joining(" "));
			throw new IllegalStateException(
					"The password of the initial administrator '" + username +
					"' does not meet the password requirements: " + messages);
		}
	}
}
