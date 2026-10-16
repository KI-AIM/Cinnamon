package de.kiaim.cinnamon.test.platform.config;

import de.kiaim.cinnamon.platform.exception.BadUserException;
import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import de.kiaim.cinnamon.platform.service.UserService;
import de.kiaim.cinnamon.test.platform.ControllerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the Angular app's login establishes a session-backed login
 * (see {@link de.kiaim.cinnamon.platform.config.SecurityConfig#appFilterChain}).
 *
 * @author Daniel Preciado-Marquez
 */
@Transactional
public class SessionAuthenticationTest extends ControllerTest {

	private static final String USERNAME = "test_user";
	private static final String PASSWORD = "changeme";

	private static final String API_USER = "session_test_api_user";
	private static final String API_PASSWORD = "changeme";

	@Autowired
	private UserService userService;

	@BeforeEach
	public void createApiUser() throws BadUserException {
		userService.register(API_USER, API_PASSWORD, Set.of(UserRole.ROLE_API), null);
	}

	@Test
	public void loginEstablishesASessionUsableForSubsequentRequestsWithoutCredentials() throws Exception {
		final MvcResult loginResult = mockMvc.perform(get("/api/user/login").with(httpBasic(USERNAME, PASSWORD)))
		                                     .andExpect(status().isOk())
		                                     .andReturn();

		final MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
		assertNotNull(session, "Login did not create a session!");

		mockMvc.perform(get("/api/user/login").session(session))
		       .andExpect(status().isOk())
		       .andExpect(content().json("{username: 'test_user', roles: ['ROLE_USER']}"));
	}

	@Test
	public void withoutASessionOrCredentialsRequestsAreRejected() throws Exception {
		mockMvc.perform(get("/api/user/login"))
		       .andExpect(status().isUnauthorized());
	}

	@Test
	public void externalApiChainNeverCreatesASessionEvenWithValidCredentials() throws Exception {
		final MvcResult result = mockMvc.perform(multipart("/api/workflow").with(httpBasic(API_USER, API_PASSWORD)))
		                                .andExpect(status().isBadRequest())
		                                .andReturn();

		assertNull(result.getRequest().getSession(false),
		           "The stateless external API chain must not create a session!");
	}

}
