package de.kiaim.platform.repository;

import de.kiaim.platform.model.entity.ExternalProcessEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface ExternalProcessRepository extends CrudRepository<ExternalProcessEntity, Long> {
}
