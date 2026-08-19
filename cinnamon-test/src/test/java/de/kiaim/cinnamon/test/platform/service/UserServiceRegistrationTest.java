package de.kiaim.cinnamon.test.platform.service;

import de.kiaim.cinnamon.platform.exception.BadAppStateException;
import de.kiaim.cinnamon.platform.exception.BadUserException;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import de.kiaim.cinnamon.platform.repository.UserRepository;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.platform.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for the invitation-requirement check in {@link UserService#register(String, String, String)}.
 * Uses mocked dependencies instead of a Spring context because the behaviour only depends on the
 * {@code cinnamon.users.invitation.is-required} flag passed into the constructor.
 *
 * @author Daniel Preciado-Marquez
 */
public class UserServiceRegistrationTest {

	private UserRepository userRepository;
	private PasswordEncoder passwordEncoder;
	private ProjectService projectService;

	@BeforeEach
	public void setup() {
		userRepository = mock(UserRepository.class);
		passwordEncoder = mock(PasswordEncoder.class);
		projectService = mock(ProjectService.class);

		when(userRepository.existsByUsername(any())).thenReturn(false);
		when(passwordEncoder.encode(any())).thenReturn("encoded_password");
	}

	@Test
	public void registerFailsWhenInvitationRequired() {
		final UserService userService = new UserService(true, userRepository, passwordEncoder, projectService);

		final var e = assertThrows(BadAppStateException.class,
		                           () -> userService.register("new_user", "password", null));
		assertEquals("PLATFORM_1_18_2", e.getErrorCode());

		verify(userRepository, never()).save(any());
	}

	@Test
	public void registerSucceedsWhenInvitationNotRequired() throws BadUserException, BadAppStateException {
		final UserService userService = new UserService(false, userRepository, passwordEncoder, projectService);

		final UserEntity user = userService.register("new_user", "password", "new_user@example.com");

		assertEquals("new_user", user.getUsername());
		assertEquals("encoded_password", user.getPassword());
		assertEquals("new_user@example.com", user.getEmail());
		assertEquals(Set.of(UserRole.ROLE_USER), user.getUserRoles(), "Unexpected default roles!");

		verify(userRepository).save(user);
	}

}
