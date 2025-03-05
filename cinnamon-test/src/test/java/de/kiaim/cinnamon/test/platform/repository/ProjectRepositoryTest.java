package de.kiaim.cinnamon.test.platform.repository;

import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.platform.model.entity.DataSetEntity;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.repository.ProjectRepository;
import de.kiaim.cinnamon.test.platform.DatabaseTest;
import de.kiaim.cinnamon.test.util.DataConfigurationTestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class ProjectRepositoryTest extends DatabaseTest {

	@Autowired
	ProjectRepository repository;

	@Test
	void save() {
		final DataConfiguration dataConfiguration = DataConfigurationTestHelper.generateDataConfiguration();

		final ProjectEntity entity = new ProjectEntity();

		final DataSetEntity dataSetEntity = new DataSetEntity();
		dataSetEntity.setDataConfiguration(dataConfiguration);
		entity.getOriginalData().setDataSet(dataSetEntity);

		repository.save(entity);

		assertNotNull(entity.getId());
	}

	@Test
	void load() {
		final DataConfiguration dataConfiguration = DataConfigurationTestHelper.generateDataConfiguration();

		final ProjectEntity entity = new ProjectEntity();

		final DataSetEntity dataSetEntity = new DataSetEntity();
		dataSetEntity.setDataConfiguration(dataConfiguration);
		entity.getOriginalData().setDataSet(dataSetEntity);

		repository.save(entity);
		final Optional<ProjectEntity> loadedEntity = repository.findById(entity.getId());
		assertTrue(loadedEntity.isPresent());
		assertEquals(dataConfiguration, Objects.requireNonNull(loadedEntity.get().getOriginalData().getDataSet()).getDataConfiguration());
	}

	@Test
	void delete() {
		final DataConfiguration dataConfiguration = DataConfigurationTestHelper.generateDataConfiguration();

		final ProjectEntity entity = new ProjectEntity();

		final DataSetEntity dataSetEntity = new DataSetEntity();
		dataSetEntity.setDataConfiguration(dataConfiguration);
		entity.getOriginalData().setDataSet(dataSetEntity);

		repository.save(entity);
		assertTrue(repository.existsById(entity.getId()));
		repository.deleteById(entity.getId());
		assertFalse(repository.existsById(entity.getId()));
	}
}
