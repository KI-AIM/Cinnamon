package de.kiaim.cinnamon.platform.config;

import de.kiaim.cinnamon.platform.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final PasswordEncoder passwordEncoder;
	private final UserService userService;
	private final ProjectLogContextFilter projectLogContextFilter;

	@Autowired
	public SecurityConfig(final PasswordEncoder passwordEncoder,
	                      final UserService userService,
	                      final ProjectLogContextFilter projectLogContextFilter) {
		this.passwordEncoder = passwordEncoder;
		this.userService = userService;
		this.projectLogContextFilter = projectLogContextFilter;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
//		httpSecurity.csrf(httpSecurityCsrfConfigurer -> httpSecurityCsrfConfigurer
//				            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
		httpSecurity.csrf(AbstractHttpConfigurer::disable)
		            .cors(Customizer.withDefaults())
		            .authorizeHttpRequests(authz -> authz
				            .requestMatchers(antMatcher("/api/doc"),
				                             // TODO Implement proper security
				                             antMatcher("/api/project/**/process/**/callback"),
				                             antMatcher("/api/swagger-ui/**"),
				                             // Left open, so infrastructure health checks do not need credentials.
				                             // The health details are only shown to authenticated ROLE_MONITORING
				                             // users, see management.endpoint.health.* in the application.properties.
				                             antMatcher("/actuator/health"),
				                             antMatcher("/actuator/health/**"),
				                             antMatcher("/api/user/register")).permitAll()
				            .requestMatchers(antMatcher("/actuator/**")).hasRole("MONITORING")
				            .requestMatchers(antMatcher("/api/workflow"),
				                             antMatcher("/api/workflow/**")).hasRole("API")
				            .requestMatchers(antMatcher("/api/admin"),
				                             antMatcher("/api/admin/**")).hasRole("ADMIN")
				            .requestMatchers(antMatcher("/api/**")).hasRole("USER")
				            .requestMatchers(antMatcher("/**")).permitAll()
				            .anyRequest().authenticated())
		            .httpBasic(Customizer.withDefaults())
		            .addFilterAfter(projectLogContextFilter, BasicAuthenticationFilter.class);
		return httpSecurity.build();
	}

	@Bean
	public AuthenticationManager authenticationManager() {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
		provider.setPasswordEncoder(passwordEncoder);
		provider.setUserDetailsService(userService);
		return new ProviderManager(provider);
	}
}
