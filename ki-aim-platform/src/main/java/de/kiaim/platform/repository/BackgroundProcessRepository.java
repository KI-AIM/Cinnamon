package de.kiaim.platform.repository;

import de.kiaim.platform.model.entity.BackgroundProcessEntity;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;

@Transactional(readOnly = true)
public interface BackgroundProcessRepository extends CrudRepository<BackgroundProcessEntity, Long> {
	long countByEndpointAndExternalProcessStatus(int endpoint, ProcessStatus externalProcessStatus);
	long countByEndpointInAndExternalProcessStatus(Collection<Integer> endpoints, ProcessStatus externalProcessStatus);

	Optional<BackgroundProcessEntity> findFirstByEndpointInAndExternalProcessStatusOrderByScheduledTimeAsc(
			Collection<Integer> endpoints,
			ProcessStatus externalProcessStatus);
}
