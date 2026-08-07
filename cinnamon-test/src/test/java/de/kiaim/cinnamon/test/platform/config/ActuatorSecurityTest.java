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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the actuator endpoints are only fully accessible to users with {@link UserRole#ROLE_MONITORING},
 * while {@code /actuator/health} itself stays reachable without authentication so infrastructure health checks
 * keep working.
 *
 * @author Daniel Preciado-Marquez
 */
@Transactional
public class ActuatorSecurityTest extends ControllerTest {

	private static final String MONITORING_USER = "monitoring_user";
	private static final String MONITORING_PASSWORD = "changeme";

	@Autowired
	private UserService userService;

	@BeforeEach
	public void createMonitoringUser() throws BadUserException {
		userService.register(MONITORING_USER, MONITORING_PASSWORD, Set.of(UserRole.ROLE_MONITORING));
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ /actuator/health ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	// Note: the assertions below intentionally don't pin down the overall HTTP status (200 vs. 503) or the
	// "status" value (UP vs. DOWN) - that reflects whether the app and its dependencies are actually healthy,
	// which is unrelated to what's being tested here: who is allowed to see the health *details*. The endpoint
	// itself is always permitAll, so it never responds with 401/403 regardless of the health outcome.

	@Test
	public void healthIsPubliclyAccessibleWithoutDetails() throws Exception {
		mockMvc.perform(get("/actuator/health"))
		       .andExpect(jsonPath("$.status").exists())
		       .andExpect(jsonPath("$.components").doesNotExist());
	}

	@Test
	@WithUserDetails("test_user")
	public void healthDetailsHiddenForUserWithoutMonitoringRole() throws Exception {
		mockMvc.perform(get("/actuator/health"))
		       .andExpect(jsonPath("$.status").exists())
		       .andExpect(jsonPath("$.components").doesNotExist());
	}

	@Test
	public void healthDetailsShownForUserWithMonitoringRole() throws Exception {
		mockMvc.perform(get("/actuator/health").with(httpBasic(MONITORING_USER, MONITORING_PASSWORD)))
		       .andExpect(jsonPath("$.status").exists())
		       .andExpect(jsonPath("$.components").exists());
	}

	//━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ other /actuator/** endpoints ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	public void otherActuatorEndpointsRejectUnauthenticatedRequests() throws Exception {
		mockMvc.perform(get("/actuator/env"))
		       .andExpect(status().isUnauthorized());
	}

	@Test
	@WithUserDetails("test_user")
	public void otherActuatorEndpointsRejectUserWithoutMonitoringRole() throws Exception {
		mockMvc.perform(get("/actuator/env"))
		       .andExpect(status().isForbidden());
	}

	@Test
	public void otherActuatorEndpointsAllowUserWithMonitoringRole() throws Exception {
		// The endpoint itself is not exposed (see management.endpoints.web.exposure.include), so it 404s once
		// past the security layer. What matters here is that authorization is no longer rejected (401/403).
		mockMvc.perform(get("/actuator/env").with(httpBasic(MONITORING_USER, MONITORING_PASSWORD)))
		       .andExpect(status().isNotFound());
	}

}
