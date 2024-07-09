package de.kiaim.platform.repository;

import de.kiaim.platform.model.entity.ProjectEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface ProjectRepository extends CrudRepository<ProjectEntity, Long> {
}
