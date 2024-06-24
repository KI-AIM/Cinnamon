package de.kiaim.platform;

import de.kiaim.platform.model.entity.UserEntity;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import java.util.List;

//@TestConfiguration
public class TestSecurityConfig {

//	@Bean
//	@Primary
//	public UserDetailsService userDetailsService() {
//		UserEntity user = new UserEntity();
//		user.setEmail("email");
//		user.setPassword("password");
//
//		return new InMemoryUserDetailsManager(List.of(user));
//	}
}
