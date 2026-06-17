package de.kiaim.cinnamon.test.platform.helper;

import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CinnamonConfigurationPostProcessorTest extends ContextRequiredTest {

	@Autowired
	private CinnamonConfiguration config;

	@Autowired
	private Environment environment;

	@Test
	public void assignIndices() {
		// Test external server instances
		for (final var externalServer : config.getExternalServer().values()) {
			for (final var entry : externalServer.getInstances().entrySet()) {
				assertEquals(entry.getKey(), entry.getValue().getName());
			}
		}
	}

	@Test
	public void link() {
		// Test external server instances
		for (final var externalServer : config.getExternalServer().values()) {
			for (final var entry : externalServer.getInstances().values()) {
				assertEquals(externalServer, entry.getServer());
			}
		}

	}

	@Test
	public void mapProcessEndpointTimeouts() {
		for (final var entry : config.getExternalServerEndpoints().entrySet()) {
			final var index = entry.getKey();
			final var endpoint = entry.getValue();

			final var configuredConnectTimeout = environment.getProperty(
					"cinnamon.external-server-endpoints." + index + ".process-endpoint-timeout.connect",
					Duration.class
			);
			final var configuredResponseTimeout = environment.getProperty(
					"cinnamon.external-server-endpoints." + index + ".process-endpoint-timeout.response",
					Duration.class
			);

			assertEquals(
					configuredConnectTimeout != null ? configuredConnectTimeout : Duration.ofSeconds(10),
					endpoint.getProcessEndpointTimeout().getConnect()
			);
			assertEquals(
					configuredResponseTimeout != null ? configuredResponseTimeout : Duration.ofSeconds(10),
					endpoint.getProcessEndpointTimeout().getResponse()
			);
		}
	}


}
