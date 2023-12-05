package de.kiaim.platform.config;

import de.kiaim.platform.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final PasswordEncoder passwordEncoder;
	private final UserService userService;

	@Autowired
	public SecurityConfig(final PasswordEncoder passwordEncoder, final UserService userService) {
		this.passwordEncoder = passwordEncoder;
		this.userService = userService;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
		httpSecurity.csrf(AbstractHttpConfigurer::disable)
		            .authorizeHttpRequests(authz -> authz
				            .requestMatchers(antMatcher("/"),
				                             antMatcher("/api/doc"),
				                             antMatcher("/api-docs/**"),
				                             antMatcher("/api/swagger-ui/**"),
				                             antMatcher("/api/user/register"))
				            .permitAll()
				            .requestMatchers(antMatcher("/**")).hasRole("USER")
				            .anyRequest().authenticated())
		            .httpBasic(Customizer.withDefaults())
                    .authenticationProvider(daoAuthenticationProvider());
		return httpSecurity.build();
	}

	@Bean
	public DaoAuthenticationProvider daoAuthenticationProvider() {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
		provider.setPasswordEncoder(passwordEncoder);
		provider.setUserDetailsService(userService);
		return provider;
	}
}
