package de.kiaim.cinnamon.test.platform.config;

import de.kiaim.cinnamon.platform.repository.AppSettingsRepository;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link de.kiaim.cinnamon.platform.config.AppSettingsInitializer}
 *
 * @author Daniel Preciado-Marquez
 */
@Transactional
public class AppSettingsInitializerTest extends ContextRequiredTest {

	@Autowired
	private AppSettingsRepository appSettingsRepository;

	@Test
	public void appSettingsCreated() {
		var count = appSettingsRepository.count();
		assertEquals(1, count, "There should be exactly one app settings entity after initialization!");
	}


}
