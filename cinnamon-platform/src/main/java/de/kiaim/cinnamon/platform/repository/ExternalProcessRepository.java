package de.kiaim.cinnamon.platform.repository;

import de.kiaim.cinnamon.platform.model.entity.ExternalProcessEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface ExternalProcessRepository extends CrudRepository<ExternalProcessEntity, Long> {
	Optional<ExternalProcessEntity> findByUuid(UUID uuid);
}
