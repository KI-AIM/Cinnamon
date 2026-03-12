package de.kiaim.cinnamon.platform.repository;

import de.kiaim.cinnamon.model.enumeration.ProcessStatus;
import de.kiaim.cinnamon.platform.model.entity.BackgroundProcessEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface BackgroundProcessRepository extends CrudRepository<BackgroundProcessEntity, Long> {
	Optional<BackgroundProcessEntity> findByUuid(UUID uuid);

	long countByServerInstance(String instance);

	long countByServerInstanceIn(Collection<String> instance);

	List<BackgroundProcessEntity> findByEndpointInAndExternalProcessStatusOrderByScheduledTimeAsc(
			Collection<Integer> endpoints,
			ProcessStatus externalProcessStatus);
}
