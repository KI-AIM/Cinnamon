package de.kiaim.cinnamon.platform.repository;

import de.kiaim.cinnamon.platform.model.entity.DataSetEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface DataSetRepository extends CrudRepository<DataSetEntity, Long> {
}
