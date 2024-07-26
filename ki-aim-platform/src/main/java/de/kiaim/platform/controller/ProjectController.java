package de.kiaim.platform.controller;

import de.kiaim.model.spring.CustomMediaType;
import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.entity.StatusEntity;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.model.enumeration.Mode;
import de.kiaim.platform.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/project")
@Tag(name = "/api/project", description = "API for managing projects.")
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(final ProjectService projectService) {
		this.projectService = projectService;
	}

	@Operation(summary = "Creates a projects with the given mode.",
	           description = "Creates a projects with the given mode.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Response contains the status.",
			             content = @Content(schema = @Schema(implementation = StatusEntity.class))),
	})
	@PostMapping(value = "",
	             consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public StatusEntity createProject(
			@Parameter(description = "Mode of the project.", required = true)
			@RequestParam() final Mode mode,
			@AuthenticationPrincipal final UserEntity user
	) {
		final ProjectEntity project = projectService.getProject(user);
		projectService.setMode(project, mode);
		return project.getStatus();
	}

	@Operation(summary = "Returns the status of the user's project.",
	           description = "Returns the status of the user's project.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Response contains the status.",
			             content = @Content(schema = @Schema(implementation = StatusEntity.class))),
	})
	@GetMapping(value = "/status",
	            produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public StatusEntity getProjectStatus(
			@AuthenticationPrincipal final UserEntity requestUser
	) {
		return projectService.getProject(requestUser).getStatus();
	}

}
