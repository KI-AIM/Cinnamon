package de.kiaim.cinnamon.test.platform.config;

import de.kiaim.cinnamon.platform.exception.BadUserException;
import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import de.kiaim.cinnamon.platform.service.UserService;
import de.kiaim.cinnamon.test.platform.ControllerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the CSRF protection configured in {@link de.kiaim.cinnamon.platform.config.SecurityConfig}:
 * browser/session based endpoints require a valid CSRF token, while {@code /api/workflow} stays usable by
 * plain HTTP Basic Auth clients that never obtain one.
 * <p>
 * These tests use their own {@link MockMvc}, built without the CSRF-defaulting
 * {@link de.kiaim.cinnamon.test.platform.TestMockMvcCsrfConfig} that the shared {@code mockMvc} of
 * {@link ControllerTest} applies, since that default would otherwise mask a missing token.
 *
 * @author Daniel Preciado-Marquez
 */
@Transactional
public class CsrfProtectionTest extends ControllerTest {

	private static final String API_USER = "csrf_test_api_user";
	private static final String API_PASSWORD = "changeme";

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserService userService;

	private MockMvc rawMockMvc;

	@BeforeEach
	public void setUpRawMockMvc() {
		rawMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
		                            .apply(springSecurity())
		                            .build();
	}

	@BeforeEach
	public void createApiUser() throws BadUserException {
		userService.register(API_USER, API_PASSWORD, Set.of(UserRole.ROLE_API), null);
	}

	@Test
	@WithUserDetails("test_user")
	public void rejectsStateChangingRequestWithoutCsrfToken() throws Exception {
		rawMockMvc.perform(post("/api/user/-/update-username")
				                   .contentType(MediaType.APPLICATION_JSON)
				                   .content("{\"newUsername\": \"csrf_test_user\", \"currentPassword\": \"changeme\"}"))
		          .andExpect(status().isForbidden());
	}

	@Test
	public void workflowStaysAccessibleByPlainHttpBasicAuthWithoutCsrfToken() throws Exception {
		// Past the security layer (including CSRF): rejected for missing the required "configuration" part,
		// not for auth or a missing CSRF token (400, not 401/403).
		rawMockMvc.perform(multipart("/api/workflow").with(httpBasic(API_USER, API_PASSWORD)))
		          .andExpect(status().isBadRequest());
	}

}
