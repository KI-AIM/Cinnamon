package de.kiaim.platform.service;

import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService implements UserDetailsService {

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	@Autowired
	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	public boolean doesUserWithEmailExist(final String email) {
		return userRepository.existsById(email);
	}

	public void save(final String email, final String rawPassword) {
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
	}

	//==============================
	// Implementation of UserDetailsService
	//==============================

	@Override
	public UserDetails loadUserByUsername(final String email) throws UsernameNotFoundException {
		return userRepository.findById(email).orElseThrow(
				() -> new UsernameNotFoundException("User with email" + email + "not found!"));
	}
}
