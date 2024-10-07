package de.kiaim.platform.controller;

import de.kiaim.model.spring.CustomMediaType;
import de.kiaim.platform.exception.*;
import de.kiaim.platform.model.dto.ErrorResponse;
import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.entity.StatusEntity;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.model.enumeration.Mode;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.service.ProjectService;
import de.kiaim.platform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;

@RestController
@RequestMapping("/api/project")
@Tag(name = "/api/project", description = "API for managing projects.")
public class ProjectController {

	private final ProjectService projectService;
	private final UserService userService;

	public ProjectController(final ProjectService projectService, final UserService userService) {
		this.projectService = projectService;
		this.userService = userService;
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

	@Operation(summary = "Creates a ZIP file containing all files related to the project.",
	           description = "Creates a ZIP file containing all files related to the project.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Returns the ZIP file.",
			             content = @Content(schema = @Schema(implementation = Void.class),
			                                mediaType = CustomMediaType.APPLICATION_ZIP_VALUE)),
			@ApiResponse(responseCode = "400",
			             description = "No data exist.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
			@ApiResponse(responseCode = "500",
			             description = "The ZIP file could not be created.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
	})
	@GetMapping(value = "/zip",
	            produces = {CustomMediaType.APPLICATION_ZIP_VALUE})
	public ResponseEntity<StreamingResponseBody> getZip(@AuthenticationPrincipal final UserEntity requestUser,
	                                                    final HttpServletResponse response)
			throws IOException, InternalDataSetPersistenceException, InternalIOException, BadColumnNameException, BadDataSetIdException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);

		response.setContentType("application/zip");
		response.setHeader("Content-Disposition", "attachment; filename=process.zip");

		projectService.createZipFile(project, response.getOutputStream());

		return ResponseEntity.ok().build();
	}

	/**
	 * TODO move into project or process?
	 * @param executionStepName
	 * @param processStepName
	 * @param name
	 * @param requestUser
	 * @return
	 * @throws BadStepNameException
	 */
	@GetMapping(value = "/resultFile", produces = {MediaType.ALL_VALUE})
	@Transactional(readOnly = true)
	public ResponseEntity<Object> getResultFile(
			@RequestParam final String executionStepName,
			@RequestParam final String processStepName,
			@RequestParam final String name,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws BadStepNameException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);

		final Step executionStep = Step.getStepOrThrow(executionStepName);
		final Step processStep = Step.getStepOrThrow(processStepName);

		final var content =  project.getExecutions().get(executionStep).getProcesses().get(processStep).getAdditionalResultFiles().get(name);
		final var s = new String(content);

		return ResponseEntity.ok().body(s);
	}

}
