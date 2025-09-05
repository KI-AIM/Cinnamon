package de.kiaim.cinnamon.test.platform.helper;

import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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


}
