package de.kiaim.cinnamon.test.platform.config;

import de.kiaim.cinnamon.platform.config.ReportRenderTokenFilter;
import de.kiaim.cinnamon.platform.service.ReportRenderTokenService;
import de.kiaim.cinnamon.test.platform.ControllerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the authentication of the headless browser rendering the report, see {@link ReportRenderTokenFilter}.
 * Uses its own {@link MockMvc} without the CSRF defaults of {@link ControllerTest},
 * so that the CSRF exemption for token requests is tested as well.
 *
 * @author Daniel Preciado-Marquez
 */
public class ReportRenderTokenFilterTest extends ControllerTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private ReportRenderTokenService reportRenderTokenService;

	private MockMvc rawMockMvc;
	private String projectId;
	private String token;

	@BeforeEach
	public void setUp() {
		rawMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
		                            .apply(springSecurity())
		                            .build();
		projectId = testProject.getExternalId().toString();
		token = reportRenderTokenService.issue(testUser.getUsername(), projectId);
	}

	@Test
	public void allowsReadingTheProject() throws Exception {
		rawMockMvc.perform(get("/api/project/" + projectId)
				                   .header(ReportRenderTokenService.HEADER_NAME, token))
		          .andExpect(status().isOk());
	}

	@Test
	public void rejectsInvalidToken() throws Exception {
		rawMockMvc.perform(get("/api/project/" + projectId)
				                   .header(ReportRenderTokenService.HEADER_NAME, "invalid"))
		          .andExpect(status().isUnauthorized());
	}

	@Test
	public void rejectsOtherProjects() throws Exception {
		rawMockMvc.perform(get("/api/project/" + UUID.randomUUID())
				                   .header(ReportRenderTokenService.HEADER_NAME, token))
		          .andExpect(status().isForbidden());
	}

	@Test
	public void rejectsModifyingRequests() throws Exception {
		rawMockMvc.perform(post("/api/project/" + projectId + "/data")
				                   .header(ReportRenderTokenService.HEADER_NAME, token))
		          .andExpect(status().isForbidden());
	}

	@Test
	public void rejectsUserEndpoints() throws Exception {
		rawMockMvc.perform(get("/api/user")
				                   .header(ReportRenderTokenService.HEADER_NAME, token))
		          .andExpect(status().isForbidden());
	}

	@Test
	public void recordsRenderErrorWithoutCsrfToken() throws Exception {
		rawMockMvc.perform(post("/api/project/" + projectId + "/report/render-error")
				                   .header(ReportRenderTokenService.HEADER_NAME, token)
				                   .contentType(MediaType.TEXT_PLAIN)
				                   .content("Something failed"))
		          .andExpect(status().isOk());

		assertEquals("Something failed", reportRenderTokenService.getError(token));
	}

	@Test
	public void renderErrorRequiresToken() throws Exception {
		rawMockMvc.perform(post("/api/project/" + projectId + "/report/render-error")
				                   .contentType(MediaType.TEXT_PLAIN)
				                   .content("Something failed"))
		          .andExpect(status().isForbidden());
	}

	@Test
	public void allowedRequests() {
		final String project = "/api/project/" + projectId;

		assertTrue(ReportRenderTokenFilter.isAllowedRequest("GET", project, projectId));
		assertTrue(ReportRenderTokenFilter.isAllowedRequest("GET", project + "/statistics", projectId));
		assertTrue(ReportRenderTokenFilter.isAllowedRequest("GET", "/api/step/anonymization", projectId));
		assertTrue(ReportRenderTokenFilter.isAllowedRequest("POST", project + "/report/render-error", projectId));

		assertFalse(ReportRenderTokenFilter.isAllowedRequest("GET", project + "-other", projectId));
		assertFalse(ReportRenderTokenFilter.isAllowedRequest("DELETE", project, projectId));
		assertFalse(ReportRenderTokenFilter.isAllowedRequest("POST", "/api/step/anonymization", projectId));
		assertFalse(ReportRenderTokenFilter.isAllowedRequest("GET", "/api/admin/users", projectId));
	}
}
