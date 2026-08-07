package de.kiaim.cinnamon.test.platform.config;

import de.kiaim.cinnamon.platform.exception.BadUserException;
import de.kiaim.cinnamon.platform.model.enumeration.UserRole;
import de.kiaim.cinnamon.platform.service.UserService;
import de.kiaim.cinnamon.test.platform.ControllerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that {@code /api/workflow} is only accessible to users with {@link UserRole#ROLE_API}, per the
 * corresponding rule in {@link de.kiaim.cinnamon.platform.config.SecurityConfig}.
 *
 * @author Daniel Preciado-Marquez
 */
@Transactional
public class WorkflowApiSecurityTest extends ControllerTest {

	private static final String API_USER = "workflow_api_user";
	private static final String API_PASSWORD = "changeme";
	private static final String UNKNOWN_WORKFLOW_ID = "9842d632-3c9c-42a5-bd86-26a2d9db2294";

	@Autowired
	private UserService userService;

	@BeforeEach
	public void createApiUser() throws BadUserException {
		userService.register(API_USER, API_PASSWORD, Set.of(UserRole.ROLE_API));
	}

	@Test
	public void rejectsUnauthenticatedRequests() throws Exception {
		mockMvc.perform(multipart("/api/workflow"))
		       .andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/workflow/" + UNKNOWN_WORKFLOW_ID))
		       .andExpect(status().isUnauthorized());
	}

	@Test
	@WithUserDetails("test_user")
	public void rejectsUserWithoutApiRole() throws Exception {
		mockMvc.perform(multipart("/api/workflow"))
		       .andExpect(status().isForbidden());

		mockMvc.perform(get("/api/workflow/" + UNKNOWN_WORKFLOW_ID))
		       .andExpect(status().isForbidden());
	}

	@Test
	public void allowsUserWithApiRole() throws Exception {
		// Past the security layer: rejected for missing the required "configuration" part, not for auth (400, not 401/403).
		mockMvc.perform(multipart("/api/workflow").with(httpBasic(API_USER, API_PASSWORD)))
		       .andExpect(status().isBadRequest());

		// Past the security layer: no workflow with this ID exists, not an auth failure (404, not 401/403).
		mockMvc.perform(get("/api/workflow/" + UNKNOWN_WORKFLOW_ID).with(httpBasic(API_USER, API_PASSWORD)))
		       .andExpect(status().isNotFound());
	}

}
