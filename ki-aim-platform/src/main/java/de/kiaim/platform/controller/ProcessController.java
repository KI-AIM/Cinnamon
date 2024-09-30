package de.kiaim.platform.controller;

import de.kiaim.model.spring.CustomMediaType;
import de.kiaim.platform.exception.*;
import de.kiaim.platform.model.dto.ErrorResponse;
import de.kiaim.platform.model.dto.ConfigureProcessRequest;
import de.kiaim.platform.model.entity.ExecutionStepEntity;
import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.service.*;
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

	private final ProcessService processService;
	private final ProjectService projectService;
	private final StatusService statusService;
	private final UserService userService;

	public ProcessController(final ProcessService processService, final ProjectService projectService,
	                         final UserService userService, final StepService stepService,
	                         StatusService statusService) {
		this.processService = processService;
		this.projectService = projectService;
		this.userService = userService;
		this.statusService = statusService;
	}

	@Operation(summary = "Returns the status of the execution.",
	           description = "Returns the status of the execution.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully returns the status object.",
			             content = @Content(schema = @Schema(implementation = ExecutionStepEntity.class))),
			@ApiResponse(responseCode = "400",
			             description = "The step name is not valid.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
			@ApiResponse(responseCode = "500",
			             description = "The Request for fetching the status from the external server failed.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))})
	})
	@GetMapping(value = "/{stepName}", produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ExecutionStepEntity getProcess(
			@Parameter(description = "Step of which the process should be canceled.")
			@PathVariable final String stepName,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);
		final Step step = Step.getStepOrThrow(stepName);

		return processService.getStatus(project, step);
	}

	@Operation(summary = "Saves the configuration and the URL of the selected process for the given step.",
	           description = "Saves the configuration and the URL of the selected process for the given step.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully saved the configuration and the URL.",
			             content = @Content(schema = @Schema())),
			@ApiResponse(responseCode = "400",
			             description = "The step name is not valid or a process is running.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
			@ApiResponse(responseCode = "500",
			             description = "The application is in an invalid state.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))})
	})
	@PostMapping(value = "/{stepName}/configure",
	             consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Void> configureProcess(
			@Parameter(description = "Step of which the process should be configured.")
			@PathVariable final String stepName,
			@ParameterObject @Valid final ConfigureProcessRequest requestData,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);
		final Step step = Step.getStepOrThrow(stepName);

		// Configure process
		processService.configureProcess(project, step, requestData.getStepName(), requestData.getUrl(),
		                                requestData.getConfiguration());

		// Go to the next step
		var configuredStep = Step.getStepOrThrow(requestData.getStepName());
		final var nextStep = switch (configuredStep) {
			case ANONYMIZATION -> Step.SYNTHETIZATION;
			case SYNTHETIZATION -> Step.EXECUTION;
			case TECHNICAL_EVALUATION -> Step.EVALUATION;
			default -> Step.ANONYMIZATION;
		};

		statusService.updateCurrentStep(project, nextStep);

		return ResponseEntity.ok().build();
	}


	@Operation(summary = "Starts the execution.",
	           description = "Starts the execution.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully started the execution. Returns the status object.",
			             content = @Content(schema = @Schema(implementation = ExecutionStepEntity.class))),
			@ApiResponse(responseCode = "400",
			             description = "The step name is not valid.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
			@ApiResponse(responseCode = "500",
			             description = "Starting the execution failed because of a failed request.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))})
	})
	@PostMapping(value = "/{stepName}/start",
				 consumes = {MediaType.ALL_VALUE},
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ExecutionStepEntity startProcess(
			@Parameter(description = "Step of which the process should be canceled.")
			@PathVariable final String stepName,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);
		final Step step = Step.getStepOrThrow(stepName);

		// Start process
		return processService.start(project, step);
	}

	@Operation(summary = "Cancels a process.",
	           description = "Cancels a process if one is scheduled or running.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully canceled the process. Returns the status object.",
			             content = @Content(schema = @Schema(implementation = ExecutionStepEntity.class))),
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
	@PostMapping(value = "/{stepName}/cancel",
	             consumes = {MediaType.ALL_VALUE},
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ExecutionStepEntity cancelProcess(
			@Parameter(description = "Step of which the process should be canceled.")
			@PathVariable final String stepName,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);
		final Step step = Step.getStepOrThrow(stepName);

		return processService.cancel(project, step);
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
			@RequestParam(name = "synthetic_data", required = false) final MultipartFile syntheticData,
			@RequestParam(name = "train", required = false) final MultipartFile trainingData,
			@RequestParam(name = "test", required = false) final MultipartFile test,
			@RequestParam(name = "model", required = false) final MultipartFile model,
			final MultipartHttpServletRequest request
	) throws ApiException {
		processService.finishProcess(processId, request.getFileMap().entrySet());
		return ResponseEntity.ok().body(null);
	}

	@PostMapping(value = "/confirm", consumes = {MediaType.ALL_VALUE})
	public ResponseEntity<String> confirm(
			@AuthenticationPrincipal final UserEntity requestUser
	) {
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);
		statusService.updateCurrentStep(project, Step.TECHNICAL_EVALUATION);
		return ResponseEntity.ok().body(null);
	}

}
