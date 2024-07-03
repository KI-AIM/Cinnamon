package de.kiaim.test.platform.repository;

import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.platform.model.entity.PlatformConfigurationEntity;
import de.kiaim.platform.repository.PlatformConfigurationRepository;
import de.kiaim.test.platform.DatabaseTest;
import de.kiaim.test.util.DataConfigurationTestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class PlatformConfigurationRepositoryTest extends DatabaseTest {

	@Autowired
	PlatformConfigurationRepository repository;

	@Test
	void save() {
		final DataConfiguration dataConfiguration = DataConfigurationTestHelper.generateDataConfiguration();

		final PlatformConfigurationEntity entity = new PlatformConfigurationEntity();
		entity.setDataConfiguration(dataConfiguration);

		repository.save(entity);

		assertNotNull(entity.getId());
	}

	@Test
	void load() {
		final DataConfiguration dataConfiguration = DataConfigurationTestHelper.generateDataConfiguration();

		final PlatformConfigurationEntity entity = new PlatformConfigurationEntity();
		entity.setDataConfiguration(dataConfiguration);

		repository.save(entity);
		final Optional<PlatformConfigurationEntity> loadedEntity = repository.findById(entity.getId());
		assertTrue(loadedEntity.isPresent());
		assertEquals(dataConfiguration, loadedEntity.get().getDataConfiguration());
	}

	@Test
	void delete() {
		final DataConfiguration dataConfiguration = DataConfigurationTestHelper.generateDataConfiguration();

		final PlatformConfigurationEntity entity = new PlatformConfigurationEntity();
		entity.setDataConfiguration(dataConfiguration);

		repository.save(entity);
		assertTrue(repository.existsById(entity.getId()));
		repository.deleteById(entity.getId());
		assertFalse(repository.existsById(entity.getId()));
	}
}
