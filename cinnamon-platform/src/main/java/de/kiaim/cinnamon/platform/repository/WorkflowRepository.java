package de.kiaim.cinnamon.platform.repository;

import de.kiaim.cinnamon.platform.model.entity.WorkflowEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

/**
 * Repository for WorkflowEntity.
 *
 * @author Daniel Preciado-Marquez
 */
public interface WorkflowRepository extends CrudRepository<WorkflowEntity, Long> {

	/**
	 * Counts the number of workflows with the given workflowId.
	 *
	 * @param workflowId The workflowId to count.
	 * @return The number of workflows with the given workflowId.
	 */
	long countByWorkflowId(UUID workflowId);

}
