package de.kiaim.cinnamon.platform.repository;

import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface ProjectRepository extends CrudRepository<ProjectEntity, Long> {

	@Query(value = "SELECT data_configuration from project_entity where id = :id", nativeQuery = true)
	String getDataConfiguration(@Param("id") Long id);
}
