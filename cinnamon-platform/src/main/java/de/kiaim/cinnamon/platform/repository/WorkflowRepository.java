package de.kiaim.cinnamon.platform.repository;

import de.kiaim.cinnamon.platform.model.entity.WorkflowEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * Repository for WorkflowEntity.
 *
 * @author Daniel Preciado-Marquez
 */
@Transactional(readOnly = true)
public interface WorkflowRepository extends CrudRepository<WorkflowEntity, Long> {

	/**
	 * Counts the number of workflows with the given workflowId.
	 *
	 * @param workflowId The workflowId to count.
	 * @return The number of workflows with the given workflowId.
	 */
	long countByWorkflowId(UUID workflowId);

	/**
	 * Returns all workflows that have expired before the given timestamp.
	 *
	 * @param expirationDate The timestamp to check against.
	 * @return A list of expired workflows.
	 */
	List<WorkflowEntity> findAllByExpirationDateBefore(Timestamp expirationDate);

}
