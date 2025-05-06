package de.kiaim.cinnamon.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * General information about a configuration.
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
}
