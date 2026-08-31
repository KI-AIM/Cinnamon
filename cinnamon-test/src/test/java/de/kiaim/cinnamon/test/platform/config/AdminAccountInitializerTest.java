package de.kiaim.cinnamon.test.platform.config;

import de.kiaim.cinnamon.platform.config.AdminAccountInitializer;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import de.kiaim.cinnamon.platform.service.UserService;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
public class AdminAccountInitializerTest extends ContextRequiredTest {

	private static final String ADMIN_USERNAME = "initial_admin";
	private static final String VALID_PASSWORD = "$tr0ngPa$$w0rd";

	@Autowired private AdminAccountInitializer adminAccountInitializer;
	@Autowired private CinnamonConfiguration cinnamonConfiguration;
	@Autowired private UserService userService;

	/**
	 * Resets the shared configuration bean.
	 * The created users are rolled back with the transaction of the test.
	 */
	@AfterEach
	public void resetAdminConfiguration() {
		cinnamonConfiguration.getAdmin().setUsername(null);
		cinnamonConfiguration.getAdmin().setPassword(null);
	}

	@Test
	public void createsAdmin() {
		configureAdmin(ADMIN_USERNAME, VALID_PASSWORD);

		assertDoesNotThrow(() -> adminAccountInitializer.run(null));

		final var admin = userService.getUserByUsername(ADMIN_USERNAME);
		assertNotNull(admin, "The initial administrator has not been created!");
		assertEquals(Set.of(UserRole.values()), admin.getUserRoles(), "Unexpected roles!");
		assertNotEquals(VALID_PASSWORD, admin.getPassword(), "Password should not be stored as clear text!");
	}

	@Test
	public void keepsExistingAdmin() {
		configureAdmin(ADMIN_USERNAME, VALID_PASSWORD);
		assertDoesNotThrow(() -> adminAccountInitializer.run(null));
		final var passwordHash = userService.getUserByUsername(ADMIN_USERNAME).getPassword();

		configureAdmin(ADMIN_USERNAME, "0th3rPa$$w0rd!");
		assertDoesNotThrow(() -> adminAccountInitializer.run(null));

		assertEquals(passwordHash, userService.getUserByUsername(ADMIN_USERNAME).getPassword(),
		             "The password of an existing administrator should not be overwritten!");
	}

	@Test
	public void ignoresUnconfiguredAdmin() {
		configureAdmin(null, null);

		assertDoesNotThrow(() -> adminAccountInitializer.run(null));

		assertNull(userService.getUserByUsername(ADMIN_USERNAME), "No administrator should have been created!");
	}

	@Test
	public void rejectsMissingPassword() {
		configureAdmin(ADMIN_USERNAME, null);

		assertThrows(IllegalStateException.class, () -> adminAccountInitializer.run(null));
		assertNull(userService.getUserByUsername(ADMIN_USERNAME), "No administrator should have been created!");
	}

	@Test
	public void rejectsWeakPassword() {
		configureAdmin(ADMIN_USERNAME, "password");

		assertThrows(IllegalStateException.class, () -> adminAccountInitializer.run(null));
		assertNull(userService.getUserByUsername(ADMIN_USERNAME), "No administrator should have been created!");
	}

	private void configureAdmin(final String username, final String password) {
		cinnamonConfiguration.getAdmin().setUsername(username);
		cinnamonConfiguration.getAdmin().setPassword(password);
	}
}
