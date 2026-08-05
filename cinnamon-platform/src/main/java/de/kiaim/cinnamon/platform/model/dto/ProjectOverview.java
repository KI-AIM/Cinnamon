package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.model.enumeration.StageStatus;
import de.kiaim.cinnamon.platform.model.enumeration.Step;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * DTO containing an overview of all important information about a project.
 * Primarily used for the project overview page in the web application.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "DTO containing an overview of all important information about a project.")
@RequiredArgsConstructor
@Getter
public class ProjectOverview {

	/**
	 * Basic information about the project.
	 */
	@Schema(description = "Basic information about the project.")
	private final ProjectInfo info;

	/**
	 * Current step of the project.
	 */
	@Schema(description = "Current step of the project.")
	private final Step currentStep;

	/**
	 * Statuses of all stages in the project.
	 */
	@Schema(description = "Statuses of all stages in the project.")
	private final List<StageStatus> stageStatuses;

}
