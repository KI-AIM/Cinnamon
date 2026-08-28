package de.kiaim.cinnamon.platform.config;

import de.kiaim.cinnamon.platform.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
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

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
		httpSecurity.csrf(csrf -> csrf
				            // Cookie is readable by JavaScript (withHttpOnlyFalse) so the Angular frontend can
				            // read it and echo it back as the X-XSRF-TOKEN header, per Angular's built-in
				            // HttpClient XSRF support.
				            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
				            // Use the plain (non-BREACH-protected) token value, since it is exposed via a
				            // cookie anyway. Required for the cookie-based SPA pattern to work, see
				            // https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html#csrf-integration-javascript-spa
				            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
				            // These endpoints are only meant to be used by plain HTTP Basic Auth clients
				            // (e.g. the Python microservices, or external API users with ROLE_API) that never
				            // load the Angular app and therefore never obtain a CSRF cookie.
				            .ignoringRequestMatchers(antMatcher("/api/workflow"),
				                                      antMatcher("/api/workflow/**"),
				                                      antMatcher("/api/project/**/process/**/callback")))
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
		            // The Angular app re-authenticates every request with Basic Auth itself, and
		            // BasicAuthenticationFilter's default SecurityContextRepository is request-scoped
		            // (not session-based), so there is normally no server-side session to end. A dedicated
		            // logout is still worthwhile: it clears the XSRF-TOKEN cookie server-side (Spring
		            // Security wires in a CsrfLogoutHandler automatically here, since CSRF is enabled
		            // above) and safely invalidates a session too on the off chance one exists, instead of
		            // the frontend merely forgetting its locally cached credentials.
		            .logout(logout -> logout
				            .logoutUrl("/api/user/logout")
				            .invalidateHttpSession(true)
				            .deleteCookies("JSESSIONID")
				            // This is an API, not a page, so respond with plain 200 instead of a redirect.
				            .logoutSuccessHandler((request, response, authentication) ->
						                                   response.setStatus(HttpServletResponse.SC_OK)))
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
