package de.kiaim.cinnamon.platform.config;

import de.kiaim.cinnamon.platform.controller.ProcessController;
import de.kiaim.cinnamon.platform.controller.UserController;
import de.kiaim.cinnamon.platform.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

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

	/**
	 * Covers the endpoints only ever used by plain HTTP Basic Auth clients that never load the Angular app and
	 * therefore never obtain a CSRF cookie or a session. This includes external modules calling the callback endpoint
	 * for processes (see {@link ProcessController#callback}), external {@code ROLE_API} users calling
	 * {@code /api/workflow}, and infrastructure/monitoring tools polling the actuator endpoints.
	 */
	@Bean
	@Order(1)
	public SecurityFilterChain externalApiFilterChain(final HttpSecurity httpSecurity) throws Exception {
		httpSecurity.securityMatchers(matchers -> matchers.requestMatchers(
				            antMatcher("/api/workflow"),
				            antMatcher("/api/workflow/**"),
				            antMatcher("/api/project/**/process/**/callback"),
				            antMatcher("/actuator/**")))
		            .csrf(AbstractHttpConfigurer::disable)
		            .cors(Customizer.withDefaults())
		            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
		            .authorizeHttpRequests(authz -> authz.requestMatchers(
				                                                 // TODO Implement proper security
				                                                 antMatcher("/api/project/**/process/**/callback"),
				                                                 antMatcher("/actuator/health"),
				                                                 antMatcher("/actuator/health/**")).permitAll()
		                                                 .requestMatchers(antMatcher("/actuator/**"))
		                                                 .hasRole("MONITORING")
		                                                 .requestMatchers(antMatcher("/api/workflow"),
		                                                                  antMatcher("/api/workflow/**"))
		                                                 .hasRole("API")
		                                                 .anyRequest().authenticated())
		            .httpBasic(Customizer.withDefaults())
		            .addFilterAfter(projectLogContextFilter, BasicAuthenticationFilter.class);
		return httpSecurity.build();
	}

	/**
	 * Covers the Angular app and the browser-facing {@code /api/**} it calls. The Angular app authenticates once via
	 * {@code GET /api/user/login} (Basic Auth, see {@link UserController}), and the resulting authentication is
	 * persisted into an {@link jakarta.servlet.http.HttpSession} so subsequent requests are authenticated via the
	 * {@code JSESSIONID} cookie the browser then sends automatically.
	 */
	@Bean
	@Order(2)
	public SecurityFilterChain appFilterChain(final HttpSecurity httpSecurity) throws Exception {
		httpSecurity.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
		                              .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
		            .cors(Customizer.withDefaults())
		            .authorizeHttpRequests(authz -> authz.requestMatchers(antMatcher("/api/doc"),
		                                                                  antMatcher("/api/swagger-ui/**"),
		                                                                  antMatcher("/api/user/register"))
		                                                 .permitAll()
		                                                 .requestMatchers(antMatcher("/api/admin"),
		                                                                  antMatcher("/api/admin/**"))
		                                                 .hasRole("ADMIN")
		                                                 .requestMatchers(antMatcher("/api/**")).hasRole("USER")
		                                                 .requestMatchers(antMatcher("/**")).permitAll()
		                                                 .anyRequest().authenticated())
		            .httpBasic(basic -> basic.securityContextRepository(new HttpSessionSecurityContextRepository()))
		            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
		            .logout(logout -> logout .logoutUrl("/api/user/logout")
				                              .invalidateHttpSession(true)
				                              .deleteCookies("JSESSIONID")
				                             .logoutSuccessHandler(
						                             (request, response, authentication) -> response.setStatus(
								                             HttpServletResponse.SC_OK)))
		            .addFilterAfter(projectLogContextFilter, BasicAuthenticationFilter.class)
		            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
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
