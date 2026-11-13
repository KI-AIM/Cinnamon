package de.kiaim.cinnamon.test.platform.controller;

import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.test.platform.ControllerTest;
import jakarta.persistence.EntityManager;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithUserDetails("test_user")
class TextExtractionControllerTest extends ControllerTest {

	@Autowired private CinnamonConfiguration cinnamonConfiguration;
	@Autowired private EntityManager entityManager;

	@Test
	void profilesAndConfigurationRoundTripThroughHttp() throws Exception {
		final var host = cinnamonConfiguration.getExternalHost().get("text-extraction-host");
		final String originalUrl = host.getUrl();
		try (final MockWebServer server = new MockWebServer()) {
			server.start();
			host.setUrl(server.url("/").toString());
			final String profiles = """
					{"profiles":[{"name":"Local model","provider":"ollama","model_name":"qwen3:8b"}]}
					""";
			server.enqueue(new MockResponse.Builder().addHeader("Content-Type", "application/json")
			                                        .body(profiles).build());
			mockMvc.perform(get("/api/text-extraction/profiles"))
			       .andExpect(status().isOk()).andExpect(content().json(profiles));
			assertEquals("/profiles", server.takeRequest().getUrl().encodedPath());

			final String configuration = "{\"profile\":\"Local model\",\"fields\":[]}";
			server.enqueue(new MockResponse.Builder().addHeader("Content-Type", "application/json")
			                                        .body(configuration).build());
			mockMvc.perform(post("/api/text-extraction/configuration")
			                       .contentType(MediaType.APPLICATION_JSON).content(configuration))
			       .andExpect(status().isOk()).andExpect(content().json(configuration));
			assertEquals(jsonMapper.readTree(configuration),
			             jsonMapper.readTree(server.takeRequest().getBody().utf8()));
		} finally {
			host.setUrl(originalUrl);
		}
	}

	@Test
	void detachedExtractionJsonSurvivesMergeAndStateResponse() throws Exception {
		final var configuration = jsonMapper.readTree("{\"profile\":\"Local model\",\"fields\":[]}");
		final var result = jsonMapper.readTree("{\"columns\":[\"finding\"],\"rows\":[{\"finding\":null}]}");
		final ProjectEntity project = entityManager.find(ProjectEntity.class, testProject.getId());
		entityManager.flush();
		entityManager.clear();
		project.setTextExtractionConfiguration(configuration);
		project.setTextExtractionResult(result);
		final ProjectEntity merged = entityManager.merge(project);
		assertNotSame(result, merged.getTextExtractionResult());
		entityManager.flush();
		entityManager.clear();
		final ProjectEntity reloaded = entityManager.find(ProjectEntity.class, testProject.getId());
		assertEquals(configuration, reloaded.getTextExtractionConfiguration());
		assertEquals(result, reloaded.getTextExtractionResult());
		entityManager.clear();
		((tools.jackson.databind.node.ObjectNode) reloaded.getTextExtractionResult().path("rows").get(0))
				.put("finding", "updated");
		entityManager.merge(reloaded);
		entityManager.flush();
		entityManager.clear();
		mockMvc.perform(get("/api/project/" + testProject.getExternalId() + "/text-extraction?wide=false"))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.configuration.profile").value("Local model"))
		       .andExpect(jsonPath("$.result.columns[0]").value("finding"))
		       .andExpect(jsonPath("$.result.rows[0].finding").value("updated"));
	}
}
