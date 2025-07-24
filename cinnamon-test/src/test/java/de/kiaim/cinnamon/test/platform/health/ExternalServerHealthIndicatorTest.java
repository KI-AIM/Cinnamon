package de.kiaim.cinnamon.test.platform.health;

import de.kiaim.cinnamon.platform.health.ExternalServerHealthIndicator;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExternalServerHealthIndicatorTest extends ContextRequiredTest {

	private static final int mockBackEndPort = TestSocketUtils.findAvailableTcpPort();

	@Autowired private CinnamonConfiguration cinnamonConfiguration;

	private MockWebServer mockBackEnd;

	@DynamicPropertySource
	static void dynamicProperties(DynamicPropertyRegistry registry) {
		registry.add("cinnamon.external-server.technical-evaluation-server.urlServer", () -> String.format("http://localhost:%s", mockBackEndPort));
		registry.add("cinnamon.external-server.synthetization-server.urlServer", () -> String.format("http://localhost:%s", mockBackEndPort));
		registry.add("cinnamon.external-server.synthetization-server.healthEndpoint", () -> "");
		registry.add("cinnamon.external-server.anonymization-server.urlServer", () -> String.format("http://localhost:%s", mockBackEndPort));
		registry.add("cinnamon.external-server.anonymization-server.healthEndpoint", () -> "/health");
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
