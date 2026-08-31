package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.dto.ConfirmUserRequest;
import de.kiaim.cinnamon.platform.model.dto.ProjectOverview;
import de.kiaim.cinnamon.platform.model.dto.UserInfo;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import de.kiaim.cinnamon.platform.repository.UserRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Log4j2
public class UserService implements UserDetailsService {

	private final boolean isInvitationRequired;

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final ProjectService projectService;

	@Autowired
	public UserService(
			@Value("${cinnamon.users.invitation.is-required}") boolean isInvitationRequired,
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			final ProjectService projectService
	) {
		this.isInvitationRequired = isInvitationRequired;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.projectService = projectService;
	}

	/**
	 * Returns a UserInfo object for the given user.
	 *
	 * @param user The user entity.
	 * @return The UserInfo object.
	 */
	public UserInfo getUserInfo(final UserEntity user) {
		return new UserInfo(user.getUsername(), Set.copyOf(user.getUserRoles()));
	}

	/**
	 * Returns a set of UserInfo objects for all users in the system.
	 *
	 * @return A set of UserInfo objects.
	 */
	@Transactional(readOnly = true)
	public Set<UserInfo> getAllUserInfos() {
		final Set<UserInfo> userInfos = new HashSet<>();
		this.userRepository.findAll().forEach(user -> userInfos.add(getUserInfo(user)));
		return userInfos;
	}

	/**
	 * Returns the user with the given username or null if no such user exists.
	 *
	 * @param username The username of the user.
	 * @return The user entity or null if not found.
	 */
	@Nullable
	public UserEntity getUserByUsername(final String username) {
		return userRepository.findByUsername(username).orElse(null);
	}

	/**
	 * Returns the user with the given username or throws a BadUserException if no such user exists.
	 *
	 * @param username The username of the user.
	 * @return The user entity.
	 * @throws BadUserException If the user is not found.
	 */
	@Transactional(readOnly = true)
	public UserEntity getUserByUsernameOrThrow(final String username) throws BadUserException {
		return userRepository.findByUsername(username).orElseThrow(
				() -> new BadUserException(BadUserException.NOT_FOUND, "User with username " + username + " not found"));
	}

	/**
	 * Checks if a user with the given username exists.
	 *
	 * @param username The username to check.
	 * @return True if a user with the given username exists, false otherwise.
	 */
	@Transactional(readOnly = true)
	public boolean doesUserWithUsernameExist(final String username) {
		return userRepository.existsByUsername(username);
	}

	/**
	 * Registers a new user with the given username, password, and email.
	 * The user gets the role {@link UserRole#ROLE_USER}.
	 * Checks if registration requires an invitation and throws a BadAppStateException if it does.
	 *
	 * @param username    The username of the new user.
	 * @param rawPassword The raw password of the new user.
	 * @param email       (Optional) The email of the new user.
	 * @return The newly created user entity.
	 * @throws BadAppStateException If the registration requires an invitation.
	 * @throws BadUserException     If a user with the given username already exists.
	 */
	@Transactional
	public UserEntity register(final String username, final String rawPassword, @Nullable final String email)
			throws BadAppStateException, BadUserException {
		if (isInvitationRequired) {
			throw new BadAppStateException(BadAppStateException.REGISTRATION_REQUIRES_INVITATION,
			                               "Registration requires an invitation");
		}

		return register(username, rawPassword, Set.of(UserRole.ROLE_USER), email);
	}

	/**
	 * Registers a new user with the given username, email, password, and roles.
	 * Bypasses the check for invitation requirement, so this method should only be used by admins or for testing purposes.
	 *
	 * @param username    The username of the new user.
	 * @param rawPassword The raw password of the new user.
	 * @param roles       The roles of the new user.
	 * @param email       (Optional) The email of the new user.
	 * @return The newly created user entity.
	 * @throws BadUserException If a user with the given username already exists.
	 */
	@Transactional
	public UserEntity register(final String username, final String rawPassword,
	                           final Set<UserRole> roles, @Nullable final String email)
			throws BadUserException {
		if (doesUserWithUsernameExist(username)) {
			throw new BadUserException(BadUserException.ALREADY_EXISTS,
			                           "User with username " + username + " already exists");
		}

		final UserEntity userEntity = new UserEntity();
		userEntity.setUsername(username);
		userEntity.setEmail(email);
		userEntity.setPassword(passwordEncoder.encode(rawPassword));
		userEntity.setUserRoles(roles);

		userRepository.save(userEntity);
		log.debug("Created new user with username '{}' and roles {}", username, roles);

		return userEntity;
	}

	/**
	 * Updates a user's password after confirming their current password.
	 *
	 * @param username The username of the user.
	 * @param currentPassword The user's current password.
	 * @param rawPassword The new password.
	 * @return The updated user entity.
	 * @throws BadUserException If the user is not found.
	 * @throws BadUserConfirmationException If the current password is incorrect.
	 */
	@Transactional
	public UserEntity updatePassword(final String username, final String currentPassword, final String rawPassword)
			throws BadUserException, BadUserConfirmationException {
		final UserEntity user = getUserByUsernameOrThrow(username);

		confirmPassword(currentPassword, user);

		user.setPassword(passwordEncoder.encode(rawPassword));

		log.debug("Updated password for user with username '{}'", username);

		return user;
	}

	/**
	 * Updates a user's username after confirming their password.
	 *
	 * @param username        The current username.
	 * @param currentPassword The user's current password.
	 * @param newUsername     The new username.
	 * @throws BadUserException             If the current user does not exist, or the new username is already in use.
	 * @throws BadUserConfirmationException If the password is incorrect.
	 */
	@Transactional
	public UserEntity updateUsername(final String username, final String currentPassword, final String newUsername)
			throws BadUserException, BadUserConfirmationException {
		final UserEntity user = getUserByUsernameOrThrow(username);

		confirmPassword(currentPassword, user);
		if (Objects.equals(username, newUsername)) {
			return user;
		}
		if (doesUserWithUsernameExist(newUsername)) {
			throw new BadUserException(BadUserException.ALREADY_EXISTS,
					"User with username " + newUsername + " already exists");
		}

		user.setUsername(newUsername);

		log.debug("Updated username from '{}' to '{}'", username, newUsername);

		return user;
	}

	/**
	 * Adds the given roles to the user with the given username.
	 *
	 * @param username The username of the user.
	 * @param newRoles The new roles to be added.
	 * @return The updated user entity.
	 * @throws BadUserException If the user is not found.
	 */
	@Transactional
	public UserEntity addRoles(final String username, final Set<UserRole> newRoles) throws BadUserException {
		final UserEntity user = getUserByUsernameOrThrow(username);

		newRoles.forEach(user::addRole);

		log.debug("Added roles for user with username '{}' to {}", username, user.getUserRoles());

		return user;
	}

	/**
	 * Removes the given roles from the user with the given username.
	 *
	 * @param username The username of the user.
	 * @param rolesToRemove The roles to be removed.
	 * @return The updated user entity.
	 * @throws BadAppStateException If the last admin role is being removed from the system.
	 * @throws BadUserException If the user is not found.
	 */
	@Transactional
	public UserEntity removeRoles(final String username, final Set<UserRole> rolesToRemove)
			throws BadAppStateException, BadUserException {
		final UserEntity user = getUserByUsernameOrThrow(username);

		// Check if there is at least one other user with the role ROLE_ADMIN
		// to prevent removing the last admin role from the system
		if (rolesToRemove.contains(UserRole.ROLE_ADMIN)
		    && !userRepository.existsByUsernameNotAndUserRolesContains(username, UserRole.ROLE_ADMIN)) {
			throw new BadAppStateException(BadAppStateException.REMOVING_LAST_ADMIN,
			                               "Cannot remove the last admin role from the system");
		}

		rolesToRemove.forEach(user::removeRole);

		log.debug("Removed roles for user with username '{}' to {}", username, user.getUserRoles());

		return user;
	}

	/**
	 * Confirms if the given user credentials match the given user.
	 * Meant for confirmation after the user is already authenticated.
	 *
	 * @param confirmUserRequest The request.
	 * @param user               The authenticated user.
	 * @throws BadUserConfirmationException If the username or password doesn't match.
	 */
	public void confirmUser(final ConfirmUserRequest confirmUserRequest, final UserEntity user) throws BadUserConfirmationException {
		if (!Objects.equals(confirmUserRequest.getUsername(), user.getUsername())) {
			throw new BadUserConfirmationException(BadUserConfirmationException.INVALID_USERNAME, "Username incorrect!");
		}
		confirmPassword(confirmUserRequest.getPassword(), user);
	}

	/**
	 * Confirms if the given raw password matches the given user's password.
	 * Throws a {@link BadUserConfirmationException} if the password doesn't match.
	 *
	 * @param rawPassword The raw password to check.
	 * @param user        The user whose password is being checked.
	 * @throws BadUserConfirmationException If the password doesn't match.
	 */
	private void confirmPassword(final String rawPassword, final UserEntity user) throws BadUserConfirmationException {
		if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
			throw new BadUserConfirmationException(BadUserConfirmationException.INVALID_PASSWORD, "Password incorrect!");
		}
	}

	/**
	 * Deletes the given user.
	 *
	 * @param user The user.
	 * @throws InternalDataSetPersistenceException If the data set could not be deleted due to an internal error.
	 * @throws InternalInvalidStateException       If a running process has no server instance assigned.
	 */
	@Transactional
	public void deleteUser(final UserEntity user)
			throws InternalDataSetPersistenceException, InternalInvalidStateException {
		deleteUserData(user);
		userRepository.delete(user);
		log.debug("Deleting user with username '{}'", user.getUsername());
	}

	/**
	 * Deletes all projects of the given user.
	 *
	 * @param user The user.
	 * @throws InternalDataSetPersistenceException If the data set could not be deleted due to an internal error.
	 * @throws InternalInvalidStateException       If a running process has no server instance assigned.
	 */
	@Transactional
	public void deleteUserData(final UserEntity user)
			throws InternalDataSetPersistenceException, InternalInvalidStateException {
		for (final var project : new ArrayList<>(user.getProjects())) {
			projectService.deleteProject(user, project);
		}
	}

	/**
	 * Deletes all users whose roles are all contained in the given set of roles.
	 * Users with at least one role that is not contained in the given set are kept.
	 * Consequently, no user is deleted if the given set is empty.
	 *
	 * @param roles The roles that are allowed to be deleted.
	 * @throws InternalDataSetPersistenceException If the data set could not be deleted due to an internal error.
	 * @throws InternalInvalidStateException       If a running process has no server instance assigned.
	 */
	@Transactional
	public void deleteUsersWithRoles(final Set<UserRole> roles)
			throws InternalDataSetPersistenceException, InternalInvalidStateException {
		if (roles.isEmpty()) {
			return;
		}

		for (final var user : userRepository.findAll()) {
			if (!user.getUserRoles().isEmpty() && roles.containsAll(user.getUserRoles())) {
				deleteUser(user);
			}
		}
	}

	/**
	 * Returns all projects owned by the user with the given username.
	 * @param username The username of the user.
	 * @return A set of project overviews.
	 * @throws BadUserException If the user does not exist.
	 */
	@Transactional(readOnly = true)
	public Set<ProjectOverview> getProjects(final String username) throws BadUserException {
		final UserEntity user = getUserByUsernameOrThrow(username);
		return user.getProjects().stream()
		           .map(projectService::getProjectOverview)
		           .collect(Collectors.toSet());
	}

	/**
	 * Creates a new project for the user with the given username.
	 * If no project name is given, a default name is generated.
	 * If no project seed is given, a random seed is generated.
	 *
	 * @param username    The username of the user.
	 * @param projectName The name of the project.
	 * @param projectSeed The seed for the project.
	 * @return The created project.
	 * @throws BadUserException                          If the user does not exist.
	 * @throws InternalApplicationConfigurationException If the application configuration is invalid.
	 * @throws InternalErrorException                    If an internal error occurs.
	 */
	@Transactional
	public ProjectEntity createProject(
			final String username,
			@Nullable final String projectName,
			@Nullable final Long projectSeed
	) throws InternalApplicationConfigurationException, InternalErrorException, BadUserException {
		return createProject(getUserByUsernameOrThrow(username), projectName, projectSeed);
	}

	/**
	 * Creates a new project for the given user.
	 * If no project name is given, a default name is generated.
	 * If no project seed is given, a random seed is generated.
	 *
	 * @param user        The user.
	 * @param projectName The name of the project.
	 * @param projectSeed The seed for the project.
	 * @return The created project.
	 * @throws BadUserException                          If the user does not exist.
	 * @throws InternalApplicationConfigurationException If the application configuration is invalid.
	 * @throws InternalErrorException                    If an internal error occurs.
	 */
	@Transactional
	public ProjectEntity createProject(final UserEntity user, @Nullable String projectName, @Nullable Long projectSeed)
			throws InternalApplicationConfigurationException, InternalErrorException, BadUserException {
		if (projectName == null) {
			projectName = "Project " + (user.getProjects().size() + 1);
		}
		if (projectSeed == null) {
			projectSeed = System.currentTimeMillis();
		}

		final ProjectEntity project = projectService.createProject(projectSeed, projectName);
		user.addProject(project);

		userRepository.save(user);
		log.debug("Created project with ID {} for user '{}'", project.getExternalId(), user.getUsername());

		// Return the managed instance of the project
		return user.getProjects().stream()
		           .filter(p -> p.getExternalId().equals(project.getExternalId()))
		           .findFirst()
		           .orElse(project);
	}

	//==============================
	// Implementation of UserDetailsService
	//==============================

	@Override
	public UserEntity loadUserByUsername(final String username) throws UsernameNotFoundException {
		return userRepository.findByUsername(username).orElseThrow(
				() -> new UsernameNotFoundException("User with username" + username + "not found!"));
	}
}
