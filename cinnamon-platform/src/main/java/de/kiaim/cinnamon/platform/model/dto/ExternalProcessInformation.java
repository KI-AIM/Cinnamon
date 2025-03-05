package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.platform.model.enumeration.ProcessStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Schema(description = "Information about an external process.")
@Getter @Setter
public class ExternalProcessInformation {
	@Schema(description = "The status of the external processing.")
	private ProcessStatus externalProcessStatus;

	@Schema(description = "The step.")
	private String step;

	@Schema(description = "The detailed status object retrieved from the server.")
	private String status;

	@Schema(description = "Jobs that have been applied to the corresponding data set.")
	public List<String> processSteps;
}
