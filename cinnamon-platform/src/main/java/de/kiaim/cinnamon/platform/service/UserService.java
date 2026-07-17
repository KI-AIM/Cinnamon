package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.dto.ConfirmUserRequest;
import de.kiaim.cinnamon.platform.model.dto.ProjectInfo;
import de.kiaim.cinnamon.platform.model.dto.UserInfo;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.repository.UserRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Log4j2
public class UserService implements UserDetailsService {

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final ProjectService projectService;

	@Autowired
	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
	                   final ProjectService projectService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.projectService = projectService;
	}

	public UserInfo getUserInfo(final UserEntity user) throws BadUserException {
		return new UserInfo(user.getUsername(), Set.of(user.getUserRole()));
	}

	@Nullable
	public UserEntity getUserByUsername(final String username) {
		return userRepository.findByUsername(username).orElse(null);
	}

	@Transactional(readOnly = true)
	public UserEntity getUserByUsernameOrThrow(final String username) throws BadUserException {
		return userRepository.findByUsername(username).orElseThrow(
				() -> new BadUserException(BadUserException.NOT_FOUND, "User with username " + username + " not found"));
	}

	@Transactional(readOnly = true)
	public boolean doesUserWithUsernameExist(final String username) {
		return userRepository.existsByUsername(username);
	}

	@Transactional
	public UserEntity register(final String username, final String rawPassword) throws BadUserException {
		if (doesUserWithUsernameExist(username)) {
			throw new BadUserException(BadUserException.ALREADY_EXISTS,
			                           "User with username " + username + " already exists");
		}

		final UserEntity userEntity = new UserEntity();
		userEntity.setUsername(username);
		userEntity.setPassword(passwordEncoder.encode(rawPassword));

		userRepository.save(userEntity);
		log.debug("Created new user with username '{}'", username);

		return userEntity;
	}

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
	 * Deletes all users.
	 *
	 * @throws InternalDataSetPersistenceException If the data set could not be deleted due to an internal error.
	 * @throws InternalInvalidStateException       If a running process has no server instance assigned.
	 */
	@Transactional
	public void deleteAllUsers()
			throws InternalDataSetPersistenceException, InternalInvalidStateException {
		final var users = userRepository.findAll();
		for (final var user : users) {
			deleteUser(user);
		}
	}

	@Transactional(readOnly = true)
	public Set<ProjectInfo> getProjects(final String username) throws BadUserException {
		final UserEntity user = getUserByUsernameOrThrow(username);
		return user.getProjects().stream()
		           .map(projectService::getProjectInfo)
		           .collect(Collectors.toSet());
	}

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
