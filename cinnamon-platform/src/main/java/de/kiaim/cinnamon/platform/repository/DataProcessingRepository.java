package de.kiaim.cinnamon.platform.repository;

import de.kiaim.cinnamon.platform.model.entity.DataProcessingEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for DataProcessingEntity.
 *
 * @author Daniel Preciado-Marquez
 */
@Transactional(readOnly = true)
public interface DataProcessingRepository extends CrudRepository<DataProcessingEntity, Long> {
}
