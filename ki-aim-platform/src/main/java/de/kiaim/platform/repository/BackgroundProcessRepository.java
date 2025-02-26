package de.kiaim.platform.repository;

import de.kiaim.platform.model.entity.BackgroundProcessEntity;
import de.kiaim.platform.model.entity.ExternalProcessEntity;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface BackgroundProcessRepository extends CrudRepository<BackgroundProcessEntity, Long> {
	Optional<BackgroundProcessEntity> findByUuid(UUID uuid);

	long countByEndpointAndExternalProcessStatus(int endpoint, ProcessStatus externalProcessStatus);
	long countByEndpointInAndExternalProcessStatus(Collection<Integer> endpoints, ProcessStatus externalProcessStatus);

	Optional<BackgroundProcessEntity> findFirstByEndpointInAndExternalProcessStatusOrderByScheduledTimeAsc(
			Collection<Integer> endpoints,
			ProcessStatus externalProcessStatus);
}
