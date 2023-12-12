package de.kiaim.platform.repository;

import de.kiaim.platform.model.entity.DataConfigurationEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface DataConfigurationRepository extends CrudRepository<DataConfigurationEntity, Long> {
}
