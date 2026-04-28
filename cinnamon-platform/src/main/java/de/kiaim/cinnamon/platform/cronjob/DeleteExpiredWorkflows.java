package de.kiaim.cinnamon.platform.cronjob;

import de.kiaim.cinnamon.platform.service.WorkflowService;
import de.kiaim.cinnamon.platform.model.entity.WorkflowEntity;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cronjob that deletes expired workflows.
 *
 * @author Daniel Preciado-Marquez
 */
@Component
@Log4j2
public class DeleteExpiredWorkflows {

	private final WorkflowService workflowService;

	public DeleteExpiredWorkflows(final WorkflowService workflowService) {
		this.workflowService = workflowService;
	}

	@Scheduled(cron = "0 5 2 * * ?")
	public void deleteExpiredWorkflows() {
		log.info("Deleting expired workflows...");

		final List<WorkflowEntity> expiredWorkflows = workflowService.getExpiredWorkflows();
		for (final WorkflowEntity workflow : expiredWorkflows) {
			try {
				workflowService.deleteWorkflow(workflow);
			} catch (final Exception e) {
				log.error("Error deleting workflow: {}", workflow.getWorkflowId(), e);
			}
		}
	}
}
