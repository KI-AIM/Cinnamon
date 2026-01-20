package de.kiaim.cinnamon.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * General information about a process for the current project.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "General information about a configuration.")
@Getter @AllArgsConstructor
public class ProcessInfo {

	@Schema(description = "Name of the job.")
	private final String job;

	@Schema(description = "If the corresponding job should be skipped.")
	private final boolean skip;

	@Schema(description = "If the job does not need a hold-out split or the hold-out split is present.")
	private boolean holdOutFulfilled;

	/**
	 * If the process is configured.
	 */
	@Schema(description = "If the process in configured.")
	private final boolean configured;
}
