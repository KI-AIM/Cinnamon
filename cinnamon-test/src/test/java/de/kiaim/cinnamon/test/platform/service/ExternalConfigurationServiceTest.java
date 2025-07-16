package de.kiaim.cinnamon.test.platform.service;

import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.service.ExternalConfigurationService;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExternalConfigurationServiceTest extends ContextRequiredTest {

	private static final int mockBackEndPort = TestSocketUtils.findAvailableTcpPort();

	@Autowired private CinnamonConfiguration cinnamonConfiguration;
	@Autowired private ExternalConfigurationService externalConfigurationService;

	private MockWebServer mockBackEnd;

	@DynamicPropertySource
	static void dynamicProperties(DynamicPropertyRegistry registry) {
		registry.add("cinnamon.external-server.technical-evaluation-server.urlServer", () -> String.format("http://localhost:%s", mockBackEndPort));
		registry.add("cinnamon.external-server.synthetization-server.urlServer", () -> String.format("http://localhost:%s", mockBackEndPort));
		registry.add("cinnamon.external-server.anonymization-server.urlServer", () -> String.format("http://localhost:%s", mockBackEndPort));
	}

	@BeforeEach
	void setUpMockWebServer() throws IOException {
		mockBackEnd = new MockWebServer();
		mockBackEnd.start(mockBackEndPort);
	}

	@AfterEach
	void shutDownMockWebServer() throws IOException {
		mockBackEnd.shutdown();
	}

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

		final String availableAlgorithms = assertDoesNotThrow(() -> externalConfigurationService.fetchAlgorithmDefinition(configurationName, definitionPath));
		assertEquals("algorithms", availableAlgorithms);

		// Test request
		RecordedRequest recordedRequest = mockBackEnd.takeRequest();
		assertEquals("GET", recordedRequest.getMethod());
		assertEquals("/algorithmA", recordedRequest.getPath());
	}

}
