package de.kiaim.cinnamon.platform.repository;

import de.kiaim.cinnamon.platform.model.entity.ExecutionStepEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface ExecutionStepRepository extends CrudRepository<ExecutionStepEntity, Long> {
}
