package de.kiaim.platform.controller;

import de.kiaim.model.spring.CustomMediaType;
import de.kiaim.platform.exception.*;
import de.kiaim.platform.model.dto.ErrorResponse;
import de.kiaim.platform.model.dto.StartProcessRequest;
import de.kiaim.platform.model.entity.ExternalProcessEntity;
import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.model.enumeration.ProcessStatus;
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
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/**
 * Controller for managing external processes.
 * TODO add /project/{id}
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

	@Operation(summary = "Returns the status of the process for the given step.",
	           description = "Returns the status of the process for the given step.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully returns the status of the process.",
			             content = @Content(schema = @Schema(implementation = ProcessStatus.class))),
			@ApiResponse(responseCode = "400",
			             description = "The step name does not exist.",
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
	@GetMapping("/{stepName}")
	public ProcessStatus getProcess(
			@PathVariable("stepName") final String stepName,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);

		return processService.getProcessStatus(project, stepName);
	}

	@Operation(summary = "Starts an external process.",
	           description = "Starts an external process.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully started the process.",
			             content = @Content(schema = @Schema(implementation = Void.class))),
			@ApiResponse(responseCode = "400",
			             description = "The step name is not valid.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
			@ApiResponse(responseCode = "500",
			             description = "The step has no corresponding process entity.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))})
	})
	@PostMapping(value = "/start",
	             consumes = {MediaType.MULTIPART_FORM_DATA_VALUE},
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ProcessStatus startProcess(
			@ParameterObject @Valid final StartProcessRequest requestData,
			@AuthenticationPrincipal final UserEntity requestUser
	)
			throws ApiException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);

		// Save config
		databaseService.storeConfiguration(requestData.getConfigurationName(), requestData.getConfiguration(), project);

		// Start process
		final ExternalProcessEntity process = processService.startProcess(project, requestData.getStepName(),
		                                                                  requestData.getUrl(),
		                                                                  requestData.getConfiguration());

		return process.getExternalProcessStatus();
	}

	@Operation(summary = "Cancels a process.",
	           description = "Cancels a process if one is scheduled or running.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully canceled the process.",
			             content = @Content(schema = @Schema(implementation = ProcessStatus.class))),
			@ApiResponse(responseCode = "400",
			             description = "The step name is not valid.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
			@ApiResponse(responseCode = "500",
			             description = "The step has not been configured correctly or no corresponding process entity exists.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))})
	})
	@PostMapping(value = "/cancel",
	             consumes = {MediaType.MULTIPART_FORM_DATA_VALUE},
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ProcessStatus cancelProcess(
			@RequestParam("stepName") final String stepName,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);

		final ExternalProcessEntity process = processService.cancelProcess(project, stepName);

		return process.getExternalProcessStatus();
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
			@ApiResponse(responseCode = "500",
			             description = "The response could not be processed.",
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
			@RequestParam(name = "synthetic_data", required = true) final MultipartFile syntheticData,
			@RequestParam(name = "train", required = false) final MultipartFile trainingData,
			@RequestParam(name = "test", required = false) final MultipartFile test,
			@RequestParam(name = "model", required = false) final MultipartFile model,
			final MultipartHttpServletRequest request
	) throws BadProcessIdException, InternalIOException {
		processService.finishProcess(processId, request.getFileMap().entrySet());
		return ResponseEntity.ok().body(null);
	}
}
