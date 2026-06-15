package de.kiaim.cinnamon.test.platform.helper;

import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CinnamonConfigurationPostProcessorTest extends ContextRequiredTest {

	@Autowired
	private CinnamonConfiguration config;

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
		assertEquals(Duration.ofSeconds(10),
		             config.getExternalServerEndpoints().get(0).getProcessEndpointTimeout().getConnect());
		assertEquals(Duration.ofSeconds(10),
		             config.getExternalServerEndpoints().get(0).getProcessEndpointTimeout().getResponse());
		assertEquals(Duration.ofSeconds(10),
		             config.getExternalServerEndpoints().get(1).getProcessEndpointTimeout().getConnect());
		assertEquals(Duration.ofSeconds(60),
		             config.getExternalServerEndpoints().get(1).getProcessEndpointTimeout().getResponse());
	}


}
