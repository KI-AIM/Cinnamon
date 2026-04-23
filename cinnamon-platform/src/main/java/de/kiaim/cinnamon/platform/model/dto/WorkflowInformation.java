package de.kiaim.cinnamon.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * General information about a workflow and its status.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "General information about a workflow and its status.")
@NoArgsConstructor @AllArgsConstructor
@Getter @Setter
public class WorkflowInformation {
	/**
	 * The unique identifier of the workflow.
	 * Uses a {@link java.util.UUID} internally.
	 */
	@Schema(description = "The unique identifier of the workflow.", example = "9842d632-3c9c-42a5-bd86-26a2d9db2294")
	private String workflowId;

	/**
	 * The status of the pipeline execution.
	 */
	@Schema(description = "The status of the pipeline execution.")
	private PipelineInformation pipeline;
}
