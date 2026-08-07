package de.kiaim.cinnamon.platform.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * DTO containing basic information about a project.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "DTO containing basic information about a project.")
@RequiredArgsConstructor
@Getter
public class ProjectInfo {

	/**
	 * ID of the project.
	 */
	@JsonProperty("id")
	@Schema(description = "ID of the project.", example = "123e4567-e89b-12d3-a456-426614174000")
	private final String externalId;

	/**
	 * The display name of the project.
	 */
	@Schema(description = "The display name of the project.", example = "My Project")
	private final String name;
}
