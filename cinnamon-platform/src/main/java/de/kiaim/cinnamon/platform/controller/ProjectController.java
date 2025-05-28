package de.kiaim.cinnamon.platform.controller;

import de.kiaim.cinnamon.model.spring.CustomMediaType;
import de.kiaim.cinnamon.platform.exception.BadQueryException;
import de.kiaim.cinnamon.platform.exception.BadStepNameException;
import de.kiaim.cinnamon.platform.exception.InternalDataSetPersistenceException;
import de.kiaim.cinnamon.platform.exception.InternalIOException;
import de.kiaim.cinnamon.platform.model.configuration.Job;
import de.kiaim.cinnamon.platform.model.configuration.Stage;
import de.kiaim.cinnamon.model.dto.ErrorResponse;
import de.kiaim.cinnamon.platform.model.dto.ProjectConfigurationDTO;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.StatusEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.model.enumeration.Mode;
import de.kiaim.cinnamon.platform.model.enumeration.Step;
import de.kiaim.cinnamon.platform.model.mapper.ProjectConfigurationMapper;
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
	private final StepService stepService;
	private final UserService userService;

	private final ProjectConfigurationMapper projectConfigurationMapper;

	public ProjectController(final ProjectService projectService, final StepService stepService,
	                         final UserService userService,
	                         final ProjectConfigurationMapper projectConfigurationMapper) {
		this.projectService = projectService;
		this.stepService = stepService;
		this.userService = userService;
		this.projectConfigurationMapper = projectConfigurationMapper;
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

	@PostMapping(value = "/step")
	public void postStep(
			@RequestParam(required = true) final Step step,
			@AuthenticationPrincipal final UserEntity requestUser
	) {
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);
		projectService.updateCurrentStep(project, step);
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
			@AuthenticationPrincipal final UserEntity requestUser
	) {
		final var project = projectService.getProject(requestUser);
		return projectConfigurationMapper.toDto(project.getProjectConfiguration());
	}

	@Operation(summary = "Updates the configuration of the user's project.",
	           description = "Updates the configuration of the user's project.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "The configuration has been updated.",
			             content = @Content()),
	})
	@PutMapping(value = "/configuration", consumes = {MediaType.APPLICATION_JSON_VALUE})
	public void getProjectConfiguration(
			@RequestBody @Valid final ProjectConfigurationDTO projectConfigurationDTO,
			@AuthenticationPrincipal final UserEntity requestUser
	) {
		final var project = projectService.getProject(requestUser);
		projectConfigurationMapper.updateEntity(project.getProjectConfiguration(), projectConfigurationDTO);
		projectService.saveProject(project);
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
			throws IOException, InternalDataSetPersistenceException, InternalIOException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);

		response.setContentType("application/zip");
		response.setHeader("Content-Disposition", "attachment; filename=process.zip");

		projectService.createZipFile(project, response.getOutputStream());

		return ResponseEntity.ok().build();
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
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
	})
	@GetMapping(value = "/resultFile", produces = {MediaType.ALL_VALUE})
	@Transactional(readOnly = true)
	public ResponseEntity<Object> getResultFile(
			@RequestParam final String executionStepName,
			@RequestParam final String processStepName,
			@RequestParam final String name,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws BadQueryException, BadStepNameException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);

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
