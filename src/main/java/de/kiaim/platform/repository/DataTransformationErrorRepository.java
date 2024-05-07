package de.kiaim.platform.repository;

import de.kiaim.platform.model.entity.DataTransformationErrorEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface DataTransformationErrorRepository extends CrudRepository<DataTransformationErrorEntity, Long> {

	long countByDataConfigurationId(final Long dataConfigurationId);
}
