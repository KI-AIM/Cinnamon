package de.kiaim.cinnamon.platform.repository;

import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface ProjectRepository extends CrudRepository<ProjectEntity, Long> {

	Optional<ProjectEntity> findByExternalId(UUID workflowId);

	/**
	 * Returns all workflows that have expired before the given timestamp.
	 *
	 * @param expirationDate The timestamp to check against.
	 * @return A list of expired workflows.
	 */
	List<ProjectEntity> findAllByExpirationDateBefore(Timestamp expirationDate);

	long countByExternalId(UUID workflowId);

	@Query(value = "SELECT data_configuration from project_entity where id = :id", nativeQuery = true)
	String getDataConfiguration(@Param("id") Long id);

}
