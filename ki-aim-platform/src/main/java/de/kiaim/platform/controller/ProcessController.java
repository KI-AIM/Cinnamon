package de.kiaim.platform.controller;

import de.kiaim.model.spring.CustomMediaType;
import de.kiaim.platform.exception.*;
import de.kiaim.platform.model.dto.ErrorResponse;
import de.kiaim.platform.model.dto.StartProcessRequest;
import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.entity.StatusEntity;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.service.DatabaseService;
import de.kiaim.platform.service.ProcessService;
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
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Controller for managing external processes.
 */
@RestController()
@RequestMapping("/api/process")
@Tag(name = "/api/process", description = "API for managing processes.")
public class ProcessController {

	private final DatabaseService databaseService;
	private final ProcessService processService;
	private final ProjectService projectService;
	private final UserService userService;


	public ProcessController(final DatabaseService databaseService, final ProcessService processService,
	                         final ProjectService projectService, final UserService userService) {
		this.databaseService = databaseService;
		this.processService = processService;
		this.projectService = projectService;
		this.userService = userService;
	}

	@PostMapping(value = "/start/test")
	public ResponseEntity<String> startProcess(
			@AuthenticationPrincipal final UserEntity requestUser
	) throws BadStepNameException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);
		// Start process
		processService.startProcessTest(project, "synthetization", "ctgan");

		return ResponseEntity.ok(null);
	}

	@Operation(summary = "Starts an external process.",
	           description = "Starts an external process.")
	@PostMapping(value = "/start",
	             consumes = {MediaType.MULTIPART_FORM_DATA_VALUE},
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully started the process.",
			             content = @Content(schema = @Schema(implementation = Void.class))),
			@ApiResponse(responseCode = "400",
			             description = "The project does not contain a dataset, or the step name is not valid.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
			@ApiResponse(responseCode = "500",
			             description = "The Request for starting the process failed.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))})
	})
	public StatusEntity startProcess(
			@ParameterObject @Valid final StartProcessRequest requestData,
			@AuthenticationPrincipal final UserEntity requestUser
	)
			throws InternalDataSetPersistenceException, InternalIOException, BadColumnNameException, BadDataSetIdException, InternalRequestException, BadStepNameException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);

		// Save config
		databaseService.storeConfiguration(requestData.getConfigurationName(), requestData.getConfiguration(), project);

		// Start process
		processService.startProcess(project, requestData.getStepName(), requestData.getUrl(),
		                            requestData.getConfiguration());

		return project.getStatus();
	}

	@Operation(summary = "Callback endpoint for marking processes as finished.",
	           description = "Callback endpoint for marking processes as finished.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully marked the process as finished.",
			             content = @Content(schema = @Schema(implementation = Void.class))),
			@ApiResponse(responseCode = "400",
			             description = "The process ID is not valid or the corresponding process has been canceled by the user.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
	})
	@PostMapping(value = "/{processId}/callback",
	             consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE}
	)
	public ResponseEntity<String> callback(
			@Parameter(description = "Id of the process to mark as finished.")
			@PathVariable final Long processId,
			@RequestParam(name = "synthetic_data", required = false) final MultipartFile syntheticData,
			@RequestParam(name = "train", required = false) final MultipartFile trainingData,
			@RequestParam(name = "test", required = false) final MultipartFile test,
			@RequestParam(name = "model", required = false) final MultipartFile model
	) throws BadProcessIdException {
		processService.finishProcess(processId);
		return ResponseEntity.ok().body(null);
	}

	@GetMapping(value = "/zip")
	public ResponseEntity<String> getZip(@AuthenticationPrincipal final UserEntity requestUser,
	                                     final HttpServletResponse response)
			throws IOException, InternalDataSetPersistenceException, InternalIOException, BadColumnNameException, BadDataSetIdException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);

		response.setContentType("application/zip");
		response.setHeader("Content-Disposition", "attachment; filename=process.zip");

		processService.createZipFile(project, response.getOutputStream(), "hi");

		return ResponseEntity.ok().build();
	}

}
