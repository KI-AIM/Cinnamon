package de.kiaim.cinnamon.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Information about a pipeline.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Information about a pipeline.")
@Getter @Setter
public class PipelineInformation {

	/**
	 * The index of the currently running or scheduled stage.
	 * Null if no stage is running or scheduled.
	 */
	@Schema(description = "The index of the currently running or scheduled stage. Null if no stage is running or scheduled.",
	        example = "0")
	@Nullable
	private Integer currentStageIndex;

	/**
	 * List of the stages contained in the pipeline.
	 */
	@Schema(description = "List of the stages contained in the pipeline.")
	private final List<ExecutionStepInformation> stages = new ArrayList<>();
}
