package de.kiaim.cinnamon.test.platform.service;

import de.kiaim.cinnamon.platform.exception.BadAppStateException;
import de.kiaim.cinnamon.platform.exception.BadUserConfirmationException;
import de.kiaim.cinnamon.platform.exception.BadUserException;
import de.kiaim.cinnamon.platform.model.dto.ConfirmUserRequest;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import de.kiaim.cinnamon.platform.repository.UserRepository;
import de.kiaim.cinnamon.platform.service.UserService;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class UserServiceTest extends ContextRequiredTest {

	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private UserService userService;
	@Autowired private UserRepository userRepository;

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
		var user = assertDoesNotThrow(() -> userService.register("email", "password", null));

		var project = assertDoesNotThrow(() -> userService.createProject(user, null, null));

		assertEquals(1, user.getProjects().size(), "Unexpected number of created projects!");
		assertEquals("Project 1", project.getProjectConfiguration().getProjectName());
	}

	@Test
	public void registerWithDefaultRole() {
		var user = assertDoesNotThrow(() -> userService.register("default_role_user", "password", null));

		assertEquals(Set.of(UserRole.ROLE_USER), user.getUserRoles(), "Unexpected roles!");
	}

	@Test
	public void registerWithMultipleRoles() {
		final var roles = Set.of(UserRole.ROLE_USER, UserRole.ROLE_ADMIN);

		var user = assertDoesNotThrow(() -> userService.register("multi_role_user", "password", roles, null));

		assertEquals(roles, user.getUserRoles(), "Unexpected roles!");
		assertEquals(roles.stream().map(UserRole::name).collect(Collectors.toSet()),
		             user.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet()),
		             "Unexpected authorities!");
	}

	@Test
	@Transactional
	public void addRoles() {
		var user = assertDoesNotThrow(
				() -> userService.register("add_roles_user", "password", Set.of(UserRole.ROLE_USER), null));

		var updatedUser = assertDoesNotThrow(
				() -> userService.addRoles(user.getUsername(), Set.of(UserRole.ROLE_API)));

		assertEquals(Set.of(UserRole.ROLE_USER, UserRole.ROLE_API), updatedUser.getUserRoles(), "Unexpected roles!");
	}

	@Test
	@Transactional
	public void addRolesIgnoresAlreadyPresentRoles() {
		var user = assertDoesNotThrow(
				() -> userService.register("add_existing_role_user", "password", Set.of(UserRole.ROLE_USER), null));

		var updatedUser = assertDoesNotThrow(
				() -> userService.addRoles(user.getUsername(), Set.of(UserRole.ROLE_USER)));

		assertEquals(Set.of(UserRole.ROLE_USER), updatedUser.getUserRoles(), "Unexpected roles!");
	}

	@Test
	@Transactional
	public void addRolesUserNotFound() {
		var e = assertThrows(BadUserException.class,
		                     () -> userService.addRoles("unknown_user", Set.of(UserRole.ROLE_API)));
		assertEquals("PLATFORM_1_16_1", e.getErrorCode());
	}

	@Test
	@Transactional
	public void removeRoles() {
		var user = assertDoesNotThrow(
				() -> userService.register("remove_roles_user", "password",
				                           Set.of(UserRole.ROLE_USER, UserRole.ROLE_API), null));

		var updatedUser = assertDoesNotThrow(
				() -> userService.removeRoles(user.getUsername(), Set.of(UserRole.ROLE_API)));

		assertEquals(Set.of(UserRole.ROLE_USER), updatedUser.getUserRoles(), "Unexpected roles!");
	}

	@Test
	@Transactional
	public void removeRolesIgnoresNotPresentRoles() {
		var user = assertDoesNotThrow(
				() -> userService.register("remove_missing_role_user", "password", Set.of(UserRole.ROLE_USER), null));

		var updatedUser = assertDoesNotThrow(
				() -> userService.removeRoles(user.getUsername(), Set.of(UserRole.ROLE_API)));

		assertEquals(Set.of(UserRole.ROLE_USER), updatedUser.getUserRoles(), "Unexpected roles!");
	}

	@Test
	@Transactional
	public void removeRolesUserNotFound() {
		var e = assertThrows(BadUserException.class,
		                     () -> userService.removeRoles("unknown_user", Set.of(UserRole.ROLE_API)));
		assertEquals("PLATFORM_1_16_1", e.getErrorCode());
	}

	@Test
	@Transactional
	public void removeRolesLastAdminIsProtected() {
		// Strip the admin role from any admin left over by other tests, bypassing the last-admin guard, so
		// that the newly registered user below is guaranteed to be the only admin in the system.
		userRepository.findAll().forEach(u -> {
			if (u.hasRole(UserRole.ROLE_ADMIN)) {
				u.removeRole(UserRole.ROLE_ADMIN);
				userRepository.save(u);
			}
		});

		var admin = assertDoesNotThrow(
				() -> userService.register("last_admin_user", "password", Set.of(UserRole.ROLE_ADMIN), null));

		var e = assertThrows(BadAppStateException.class,
		                     () -> userService.removeRoles(admin.getUsername(), Set.of(UserRole.ROLE_ADMIN)));
		assertEquals("PLATFORM_1_18_1", e.getErrorCode());
		assertEquals(Set.of(UserRole.ROLE_ADMIN), admin.getUserRoles(),
		             "Roles should not have been changed after a failed removal!");
	}

	@Test
	@Transactional
	public void removeRolesAdminAllowedWhenAnotherAdminExists() {
		var admin = assertDoesNotThrow(
				() -> userService.register("removable_admin_user", "password", Set.of(UserRole.ROLE_ADMIN), null));
		assertDoesNotThrow(
				() -> userService.register("other_admin_user", "password", Set.of(UserRole.ROLE_ADMIN), null));

		var updatedUser = assertDoesNotThrow(
				() -> userService.removeRoles(admin.getUsername(), Set.of(UserRole.ROLE_ADMIN)));

		assertEquals(Set.of(), updatedUser.getUserRoles(), "Unexpected roles!");
	}

	@Test
	public void deleteUsersWithRoles() {
		var deletable = assertDoesNotThrow(
				() -> userService.register("api_user", "password", Set.of(UserRole.ROLE_API), null));
		var protectedUser = assertDoesNotThrow(
				() -> userService.register("api_admin_user", "password",
				                           Set.of(UserRole.ROLE_API, UserRole.ROLE_ADMIN), null));

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
				() -> userService.register("kept_api_user", "password", Set.of(UserRole.ROLE_API), null));

		assertDoesNotThrow(() -> userService.deleteUsersWithRoles(Set.of()));

		assertTrue(userService.doesUserWithUsernameExist(user.getUsername()),
		           "No user should be deleted if no role is deletable!");

		assertDoesNotThrow(() -> userService.deleteUsersWithRoles(Set.of(UserRole.ROLE_API)));
	}

}
