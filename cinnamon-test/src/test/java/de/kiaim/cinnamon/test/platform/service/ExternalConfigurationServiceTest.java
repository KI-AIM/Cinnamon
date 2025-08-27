package de.kiaim.cinnamon.test.platform.service;

import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.service.ExternalConfigurationService;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import de.kiaim.cinnamon.test.util.WithMockWebServer;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@WithMockWebServer
public class ExternalConfigurationServiceTest extends ContextRequiredTest {

	@Autowired private CinnamonConfiguration cinnamonConfiguration;
	@Autowired private ExternalConfigurationService externalConfigurationService;

	private MockWebServer mockBackEnd;

	@Test
	public void fetchAlgorithms() {
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .body("algorithms")
				                    .build());

		final String configurationName = cinnamonConfiguration.getExternalConfiguration().keySet().iterator().next();
		final String availableAlgorithms = assertDoesNotThrow(() -> externalConfigurationService.fetchAvailableAlgorithms(configurationName));
		assertEquals("algorithms", availableAlgorithms);
	}

	@Test
	public void fetchAlgorithmDefinition() throws InterruptedException {
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .body("algorithms")
				                    .build());

		final String configurationName = cinnamonConfiguration.getExternalConfiguration().keySet().iterator().next();
		final String definitionPath = "/algorithmA";

		final String availableAlgorithms = assertDoesNotThrow(
				() -> externalConfigurationService.fetchAlgorithmDefinition(new ProjectEntity(), configurationName,
				                                                            definitionPath));
		assertEquals("algorithms", availableAlgorithms);

		// Test request
		RecordedRequest recordedRequest = mockBackEnd.takeRequest();
		assertEquals("GET", recordedRequest.getMethod());
		assertEquals("/algorithmA", recordedRequest.getPath());
	}

}
