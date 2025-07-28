package de.kiaim.cinnamon.test.platform.health;

import de.kiaim.cinnamon.platform.health.ExternalServerHealthIndicator;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import de.kiaim.cinnamon.test.util.WithMockWebServer;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@WithMockWebServer
public class ExternalServerHealthIndicatorTest extends ContextRequiredTest {

	@Autowired private CinnamonConfiguration cinnamonConfiguration;

	private MockWebServer mockBackEnd;

	@DynamicPropertySource
	static void dynamicProperties(DynamicPropertyRegistry registry) {
		registry.add("cinnamon.external-server.synthetization-server.healthEndpoint", () -> "");
		registry.add("cinnamon.external-server.anonymization-server.healthEndpoint", () -> "/health");
	}

	@Test
	public void testHealthUp() {
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(200)
				                    .body("{\"status\": \"UP\"}")
				                    .build());

		var indicator = new ExternalServerHealthIndicator(cinnamonConfiguration.getExternalServer().get("anonymization-server"));
		var health = indicator.health();
		assertEquals("UP", health.getStatus().getCode());
	}

	@Test
	public void testHealthDown() {
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(502)
				                    .body("{\"status\": \"DOWN\"}")
				                    .build());

		var indicator = new ExternalServerHealthIndicator(cinnamonConfiguration.getExternalServer().get("anonymization-server"));
		var health = indicator.health();
		assertEquals("DOWN", health.getStatus().getCode());
	}

	@Test
	public void testHealthDownNotFound() {
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.TEXT_PLAIN_VALUE)
				                    .code(404)
				                    .body("NOT FOUND")
				                    .build());

		var indicator = new ExternalServerHealthIndicator(cinnamonConfiguration.getExternalServer().get("anonymization-server"));
		var health = indicator.health();
		assertEquals("DOWN", health.getStatus().getCode());
	}

	@Test
	public void testHealthUnknown() {
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.TEXT_PLAIN_VALUE)
				                    .code(404)
				                    .body("NOT FOUND")
				                    .build());

		var indicator = new ExternalServerHealthIndicator(cinnamonConfiguration.getExternalServer().get("synthetization-server"));
		var health = indicator.health();
		assertEquals("UNKNOWN", health.getStatus().getCode());
	}

	@Test
	public void testHealthDownNoConnection() throws IOException {
		mockBackEnd.shutdown();

		var indicator = new ExternalServerHealthIndicator(cinnamonConfiguration.getExternalServer().get("anonymization-server"));
		var health = indicator.health();
		assertEquals("DOWN", health.getStatus().getCode());
	}

}
