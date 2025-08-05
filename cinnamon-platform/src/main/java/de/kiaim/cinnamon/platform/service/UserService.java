package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.platform.exception.BadStateException;
import de.kiaim.cinnamon.platform.exception.BadUserConfirmationException;
import de.kiaim.cinnamon.platform.exception.InternalDataSetPersistenceException;
import de.kiaim.cinnamon.platform.model.dto.ConfirmUserRequest;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
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

	@Nullable
	public UserEntity getUserByEmail(final String email) {
		return userRepository.findById(email).orElse(null);
	}

	public boolean doesUserWithEmailExist(final String email) {
		return userRepository.existsById(email);
	}

	public UserEntity save(final String email, final String rawPassword) {
		Optional<UserEntity> user = userRepository.findById(email);
		UserEntity userEntity;
		if (user.isEmpty()) {
			userEntity = new UserEntity();
			userEntity.setPassword(passwordEncoder.encode(rawPassword));
		} else {
			userEntity = user.get();
		}
		userEntity.setEmail(email);
		userEntity.setPassword(passwordEncoder.encode(rawPassword));
		userRepository.save(userEntity);

		return userEntity;
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
		if (!Objects.equals(confirmUserRequest.getEmail(), user.getEmail())) {
			throw new BadUserConfirmationException(BadUserConfirmationException.INVALID_EMAIL, "Username incorrect!");
		}
		if (!passwordEncoder.matches(confirmUserRequest.getPassword(), user.getPassword())) {
			throw new BadUserConfirmationException(BadUserConfirmationException.INVALID_PASSWORD, "Password incorrect!");
		}
	}

	/**
	 * Deletes the given user.
	 *
	 * @param user The user.
	 * @throws BadStateException                   If a process of the stage is running.
	 * @throws InternalDataSetPersistenceException If the data set could not be deleted due to an internal error.
	 */
	@Transactional
	public void deleteUser(final UserEntity user) throws BadStateException, InternalDataSetPersistenceException {
		projectService.deleteProject(user);
		userRepository.delete(user);
	}

	/**
	 * Deletes all users.
	 *
	 * @throws BadStateException                   If a process of the stage is running.
	 * @throws InternalDataSetPersistenceException If the data set could not be deleted due to an internal error.
	 */
	@Transactional
	public void deleteAllUsers() throws BadStateException, InternalDataSetPersistenceException {
		final var users = userRepository.findAll();
		for (final var user : users) {
			deleteUser(user);
		}
	}

	//==============================
	// Implementation of UserDetailsService
	//==============================

	@Override
	public UserEntity loadUserByUsername(final String email) throws UsernameNotFoundException {
		return userRepository.findById(email).orElseThrow(
				() -> new UsernameNotFoundException("User with email" + email + "not found!"));
	}
}
