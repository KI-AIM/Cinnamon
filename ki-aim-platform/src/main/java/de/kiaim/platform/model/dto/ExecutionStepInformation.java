package de.kiaim.platform.model.dto;

import de.kiaim.platform.model.entity.ExternalProcessEntity;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class ExecutionStepInformation {

	@Schema(description = "The status of the external processing.")
	private ProcessStatus status = ProcessStatus.NOT_STARTED;

	private final List<ExternalProcessInformation> processes = new ArrayList<>();
}
