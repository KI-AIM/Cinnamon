package de.kiaim.cinnamon.platform.controller;

import de.kiaim.cinnamon.model.spring.CustomMediaType;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.configuration.Job;
import de.kiaim.cinnamon.platform.model.configuration.Stage;
import de.kiaim.cinnamon.model.dto.ErrorResponse;
import de.kiaim.cinnamon.model.configuration.project.ProjectConfigurationDTO;
import de.kiaim.cinnamon.platform.model.dto.ConfirmUserRequest;
import de.kiaim.cinnamon.platform.model.dto.ProjectExportParameter;
import de.kiaim.cinnamon.platform.model.dto.ProjectInfo;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.StatusEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.model.enumeration.Step;
import de.kiaim.cinnamon.platform.service.ExportService;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.platform.service.StepService;
import de.kiaim.cinnamon.platform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/project/{projectId}")
@Tag(name = "/api/project", description = "API for managing projects.")
public class ProjectController {

	private final ExportService exportService;
	private final ProjectService projectService;
	private final StepService stepService;
	private final UserService userService;

	public ProjectController(final ExportService exportService,
	                         final ProjectService projectService,
	                         final StepService stepService,
	                         final UserService userService) {
		this.exportService = exportService;
		this.projectService = projectService;
		this.stepService = stepService;
		this.userService = userService;
	}

	@GetMapping(value = "")
	public ProjectInfo getProject(
			@PathVariable final String projectId,
			@AuthenticationPrincipal final UserEntity user
	) throws ApiException {
		return projectService.getProjectInfo(user, projectId);
	}

	@DeleteMapping(value = "",
	               consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public void deleteProject(
			@PathVariable final String projectId,
			@ParameterObject @Valid final ConfirmUserRequest confirmUserRequest,
			@AuthenticationPrincipal final UserEntity user
	) throws ApiException {
		userService.confirmUser(confirmUserRequest, user);
		final ProjectEntity project = projectService.getProject(user, projectId);
		projectService.deleteProject(user, project);
	}

	@Operation(summary = "Returns the status of the user's project.",
	           description = "Returns the status of the user's project.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Response contains the status.",
			             content = @Content(schema = @Schema(implementation = StatusEntity.class))),
	})
	@GetMapping(value = "/status",
	            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public StatusEntity getProjectStatus(
			@PathVariable final String projectId,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		return projectService.getProject(requestUser, projectId).getStatus();
	}

	@PostMapping(value = "/step")
	public void postStep(
			@PathVariable final String projectId,
			@RequestParam(required = true) final Step step,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user, projectId);
		projectService.updateCurrentStep(project, step);
	}

	@Operation(summary = "Resets the results of the process to the given target.",
	           description = "Resets the results of the process to the given target.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully reset the project.",
			             content = @Content(schema = @Schema(implementation = ProjectConfigurationDTO.class))),
			@ApiResponse(responseCode = "400",
			             description = "The given target is invalid or the project has a running process.",
			             content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500",
			             description = "Resetting the project failed.",
			             content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))),
	})
	@DeleteMapping(value = "/reset")
	public void resetProject(
			@PathVariable final String projectId,
			@Parameter(description = "Target identifier to reset. If missing or empty, the entire project is reset.")
			@RequestParam(required = false) final String target,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user, projectId);
		projectService.resetProject(project, target);
	}

	@Operation(summary = "Returns the configuration of the user's project.",
	           description = "Returns the configuration of the user's project.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Response contains the configurations.",
			             content = @Content(schema = @Schema(implementation = ProjectConfigurationDTO.class))),
	})
	@GetMapping(value = "/configuration", produces = {MediaType.APPLICATION_JSON_VALUE})
	public ProjectConfigurationDTO getProjectConfiguration(
			@PathVariable final String projectId,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		final var project = projectService.getProject(requestUser, projectId);
		return projectService.exportProjectConfiguration(project);
	}

	@Operation(summary = "Updates the configuration of the user's project.",
	           description = "Updates the configuration of the user's project.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "The configuration has been updated.",
			             content = @Content()),
	})
	@PutMapping(value = "/configuration", consumes = {MediaType.APPLICATION_JSON_VALUE})
	public void setProjectConfiguration(
			@PathVariable final String projectId,
			@RequestBody @Valid final ProjectConfigurationDTO projectConfigurationDTO,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		final var project = projectService.getProject(requestUser, projectId);
		projectService.updateProjectConfiguration(project, projectConfigurationDTO);
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
			             content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500",
			             description = "The ZIP file could not be created.",
			             content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))),
	})
	@GetMapping(value = "/zip",
	            produces = {CustomMediaType.APPLICATION_ZIP_VALUE})
	public ResponseEntity<StreamingResponseBody> getZip(
			@PathVariable final String projectId,
			@AuthenticationPrincipal final UserEntity requestUser,
			@ParameterObject final ProjectExportParameter projectExportParameter,
			final HttpServletResponse response
	) throws ApiException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user, projectId);

		return exportService.createZipFile(project, response, projectExportParameter);
	}

	@Operation(summary = "Returns a file of the result of the specified job.",
	           description = "Returns a file of the result of the specified job.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Returns the content of the file.",
			             content = @Content(schema = @Schema(implementation = String.class),
			                                mediaType = MediaType.ALL_VALUE)),
			@ApiResponse(responseCode = "400",
			             description = "No the job or the file does not exist.",
			             content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))),
	})
	@GetMapping(value = "/resultFile", produces = {MediaType.ALL_VALUE})
	@Transactional(readOnly = true)
	public ResponseEntity<Object> getResultFile(
			@PathVariable final String projectId,
			@RequestParam final String executionStepName,
			@RequestParam final String processStepName,
			@RequestParam final String name,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user, projectId);

		final Stage stage = stepService.getStageConfiguration(executionStepName);
		final Job job = stepService.getStepConfiguration(processStepName);

		final var content = project.getPipelines().get(0).getStageByStep(stage)
		                           .getProcess(job).get()
		                           .getResultFiles().get(name);
		if (content == null) {
			throw new BadQueryException(BadQueryException.RESULT_FILE, "The file '" + name + "' could not be found!");
		}
		final var s = content.getLobString();

		return ResponseEntity.ok().body(s);
	}

}
