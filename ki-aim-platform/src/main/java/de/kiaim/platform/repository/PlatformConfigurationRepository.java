package de.kiaim.platform.repository;

import de.kiaim.platform.model.entity.PlatformConfigurationEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface PlatformConfigurationRepository extends CrudRepository<PlatformConfigurationEntity, Long> {
}
