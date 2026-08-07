package de.kiaim.cinnamon.test.platform.service;

import de.kiaim.cinnamon.platform.exception.BadUserConfirmationException;
import de.kiaim.cinnamon.platform.model.dto.ConfirmUserRequest;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import de.kiaim.cinnamon.platform.service.UserService;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;
import java.util.stream.Collectors;

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
		user.setUsername(email);
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
		user.setUsername(email);
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
		user.setUsername(email);
		user.setPassword(passwordEncoded);

		var e = assertThrows(BadUserConfirmationException.class, () -> userService.confirmUser(request, user));
		assertEquals("PLATFORM_1_12_2", e.getErrorCode());
	}

	@Test
	public void createProject() {
		var user = assertDoesNotThrow(() -> userService.register("email", "password"));

		var project = assertDoesNotThrow(() -> userService.createProject(user, null, null));

		assertEquals(1, user.getProjects().size(), "Unexpected number of created projects!");
		assertEquals("Project 1", project.getProjectConfiguration().getProjectName());
	}

	@Test
	public void registerWithDefaultRole() {
		var user = assertDoesNotThrow(() -> userService.register("default_role_user", "password"));

		assertEquals(Set.of(UserRole.ROLE_USER), user.getUserRoles(), "Unexpected roles!");
	}

	@Test
	public void registerWithMultipleRoles() {
		final var roles = Set.of(UserRole.ROLE_USER, UserRole.ROLE_ADMIN);

		var user = assertDoesNotThrow(() -> userService.register("multi_role_user", "password", roles));

		assertEquals(roles, user.getUserRoles(), "Unexpected roles!");
		assertEquals(roles.stream().map(UserRole::name).collect(Collectors.toSet()),
		             user.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet()),
		             "Unexpected authorities!");
	}

	@Test
	public void deleteUsersWithRoles() {
		var deletable = assertDoesNotThrow(
				() -> userService.register("api_user", "password", Set.of(UserRole.ROLE_API)));
		var protectedUser = assertDoesNotThrow(
				() -> userService.register("api_admin_user", "password",
				                           Set.of(UserRole.ROLE_API, UserRole.ROLE_ADMIN)));

		assertDoesNotThrow(() -> userService.deleteUsersWithRoles(Set.of(UserRole.ROLE_API)));

		assertFalse(userService.doesUserWithUsernameExist(deletable.getUsername()),
		            "User with only deletable roles should have been deleted!");
		assertTrue(userService.doesUserWithUsernameExist(protectedUser.getUsername()),
		           "User with a role that is not deletable should have been kept!");
		assertTrue(userService.doesUserWithUsernameExist("test_user"),
		           "User with an unrelated role should have been kept!");

		assertDoesNotThrow(() -> userService.deleteUsersWithRoles(Set.of(UserRole.ROLE_API, UserRole.ROLE_ADMIN)));
	}

	@Test
	public void deleteUsersWithRolesEmpty() {
		var user = assertDoesNotThrow(
				() -> userService.register("kept_api_user", "password", Set.of(UserRole.ROLE_API)));

		assertDoesNotThrow(() -> userService.deleteUsersWithRoles(Set.of()));

		assertTrue(userService.doesUserWithUsernameExist(user.getUsername()),
		           "No user should be deleted if no role is deletable!");

		assertDoesNotThrow(() -> userService.deleteUsersWithRoles(Set.of(UserRole.ROLE_API)));
	}

}
