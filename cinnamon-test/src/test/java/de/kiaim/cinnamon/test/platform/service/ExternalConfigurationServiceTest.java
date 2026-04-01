package de.kiaim.cinnamon.test.platform.service;

import de.kiaim.cinnamon.model.configuration.algorithms.AlgorithmDefinition;
import de.kiaim.cinnamon.model.configuration.algorithms.AvailableAlgorithms;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.service.ExternalConfigurationService;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import de.kiaim.cinnamon.test.util.AlgorithmTestHelper;
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
				                    .addHeader("Content-Type", MediaType.APPLICATION_YAML_VALUE)
				                    .code(200)
				                    .body(AlgorithmTestHelper.generateAvailableAlgorithmsYaml())
				                    .build());

		final String configurationName = cinnamonConfiguration.getExternalConfiguration().keySet().iterator().next();
		final AvailableAlgorithms availableAlgorithms = assertDoesNotThrow(
				() -> externalConfigurationService.fetchAvailableAlgorithms(configurationName));

		var expected = AlgorithmTestHelper.generateAvailableAlgorithms();
		assertEquals(expected, availableAlgorithms);
	}

	@Test
	public void fetchAlgorithmsFromCache() {
		fetchAlgorithms();

		final String configurationName = cinnamonConfiguration.getExternalConfiguration().keySet().iterator().next();
		final AvailableAlgorithms availableAlgorithms = assertDoesNotThrow(
				() -> externalConfigurationService.fetchAvailableAlgorithms(configurationName));

		var expected = AlgorithmTestHelper.generateAvailableAlgorithms();
		assertEquals(expected, availableAlgorithms);

		// Test request
		assertEquals(1, mockBackEnd.getRequestCount());
	}

	@Test
	public void fetchAlgorithmDefinition() throws InterruptedException {
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_YAML_VALUE)
				                    .code(200)
				                    .body(AlgorithmTestHelper.generateAlgorithmDefinitionYaml())
				                    .build());

		final String configurationName = cinnamonConfiguration.getExternalConfiguration().keySet().iterator().next();
		final String definitionPath = "/algorithmA";

		final AlgorithmDefinition availableAlgorithms = assertDoesNotThrow(
				() -> externalConfigurationService.fetchAlgorithmDefinition(new ProjectEntity(), configurationName,
				                                                            definitionPath));
		assertEquals("algorithmA", availableAlgorithms.getName());

		// Test request
		RecordedRequest recordedRequest = mockBackEnd.takeRequest();
		assertEquals("GET", recordedRequest.getMethod());
		assertEquals("/algorithmA", recordedRequest.getPath());
	}

	@Test
	public void fetchAlgorithmDefinitionFromCache() throws InterruptedException {
		fetchAlgorithmDefinition();

		final String configurationName = cinnamonConfiguration.getExternalConfiguration().keySet().iterator().next();
		final String definitionPath = "/algorithmA";

		final AlgorithmDefinition availableAlgorithms = assertDoesNotThrow(
				() -> externalConfigurationService.fetchAlgorithmDefinition(new ProjectEntity(), configurationName,
				                                                            definitionPath));
		assertEquals("algorithmA", availableAlgorithms.getName());

		// Test request
		assertEquals(1, mockBackEnd.getRequestCount());
	}

}
