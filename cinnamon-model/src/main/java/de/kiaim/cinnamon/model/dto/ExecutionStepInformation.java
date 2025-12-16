package de.kiaim.cinnamon.model.dto;

import de.kiaim.cinnamon.model.enumeration.ProcessStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "Information about a stage.")
@Getter @Setter
public class ExecutionStepInformation {

	@Schema(description = "Name of the stage.", example = "execution")
	private String stageName;

	@Schema(description = "The status of the external processing.", example = "RUNNING")
	private ProcessStatus status = ProcessStatus.NOT_STARTED;

	@Schema(description = "The index of the current process.", example = "0")
	private Integer currentProcessIndex;

	@Schema(description = "The list of processes in the stage.")
	private final List<ExternalProcessInformation> processes = new ArrayList<>();
}
