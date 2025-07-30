package de.kiaim.cinnamon.test.platform.service;

import de.kiaim.cinnamon.platform.exception.InternalInvalidStateException;
import de.kiaim.cinnamon.platform.model.configuration.ExternalServerInstance;
import de.kiaim.cinnamon.platform.service.StepService;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

public class StepServiceTest extends ContextRequiredTest {

	@Autowired
	private StepService stepService;

	@Test
	public void getInstanceNull() {
		var e = assertThrows(InternalInvalidStateException.class,
		                     () -> stepService.getExternalServerInstanceConfiguration(null));
		assertEquals( "PLATFORM_2_6_6", e.getErrorCode(), "Unexpected error code!");
	}

	@Test
	public void getInstance() {
		String instanceId = "anonymization-server.0";
		ExternalServerInstance esi = assertDoesNotThrow(
				() -> stepService.getExternalServerInstanceConfiguration(instanceId));
		assertEquals(instanceId, esi.getId(), "Unexpected instance id!");
	}
}
