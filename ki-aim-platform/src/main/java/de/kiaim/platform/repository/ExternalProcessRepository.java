package de.kiaim.platform.repository;

import de.kiaim.platform.model.entity.ExternalProcessEntity;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.Step;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional(readOnly = true)
public interface ExternalProcessRepository extends CrudRepository<ExternalProcessEntity, Long> {
	long countByStepAndExternalProcessStatus(Step step, ProcessStatus externalProcessStatus);

	Optional<ExternalProcessEntity> findFirstByStepAndExternalProcessStatusOrderByScheduledTimeAsc(Step step,
	                                                                                               ProcessStatus externalProcessStatus);
}
