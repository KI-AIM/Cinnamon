package de.kiaim.cinnamon.platform.controller;

import de.kiaim.cinnamon.model.dto.ErrorRequest;
import de.kiaim.cinnamon.model.spring.CustomMediaType;
import de.kiaim.cinnamon.platform.exception.ApiException;
import de.kiaim.cinnamon.platform.service.ProcessService;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.platform.service.StepService;
import de.kiaim.cinnamon.platform.service.UserService;
import de.kiaim.cinnamon.platform.model.configuration.Job;
import de.kiaim.cinnamon.platform.model.configuration.Stage;
import de.kiaim.cinnamon.model.dto.ErrorResponse;
import de.kiaim.cinnamon.platform.model.dto.ConfigureProcessRequest;
import de.kiaim.cinnamon.platform.model.dto.ExecutionStepInformation;
import de.kiaim.cinnamon.platform.model.entity.ExecutionStepEntity;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.model.mapper.ExecutionStepMapper;
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
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.UUID;

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
	private final StepService stepService;
	private final UserService userService;
	private final ExecutionStepMapper executionStepMapper;

	public ProcessController(final ProcessService processService, final ProjectService projectService,
	                         final StepService stepService, final UserService userService,
	                         final ExecutionStepMapper executionStepMapper) {
		this.processService = processService;
		this.projectService = projectService;
		this.stepService = stepService;
		this.userService = userService;
		this.executionStepMapper = executionStepMapper;
	}

	@Operation(summary = "Returns the status of the execution.",
	           description = "Returns the status of the execution.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully returns the status object.",
			             content = @Content(schema = @Schema(implementation = ExecutionStepInformation.class))),
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
	@GetMapping(value = "/{stageName}", produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ExecutionStepInformation getProcess(
			@Parameter(description = "Step of which the process should be canceled.")
			@PathVariable final String stageName,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);

		final Stage stage = stepService.getStageConfiguration(stageName);

		final ExecutionStepEntity executionStep = processService.getStatus(project, stage);
		return executionStepMapper.toDto(executionStep);
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
	@PostMapping(value = "/{stageName}/configure",
	             consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Void> configureProcess(
			@Parameter(description = "Step of which the process should be configured.")
			@PathVariable final String stageName,
			@ParameterObject @Valid final ConfigureProcessRequest requestData,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);

		final Stage stage = stepService.getStageConfiguration(stageName);
		final Job job = stepService.getStepConfiguration(requestData.getJobName());

		// Configure process
		processService.configureProcess(project, stage, job, requestData.isSkip());

		return ResponseEntity.ok().build();
	}

	@Operation(summary = "Starts the execution.",
	           description = "Starts the execution.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully started the execution. Returns the status object.",
			             content = @Content(schema = @Schema(implementation = ExecutionStepInformation.class))),
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
	@PostMapping(value = "/{stageName}/start",
				 consumes = {MediaType.ALL_VALUE},
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ExecutionStepInformation startProcess(
			@Parameter(description = "Step of which the process should be canceled.")
			@PathVariable final String stageName,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);

		final Stage stage = stepService.getStageConfiguration(stageName);

		// Start process
		final ExecutionStepEntity executionStep = processService.start(project, stage);
		return executionStepMapper.toDto(executionStep);
	}

	@Operation(summary = "Cancels a process.",
	           description = "Cancels a process if one is scheduled or running.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully canceled the process. Returns the status object.",
			             content = @Content(schema = @Schema(implementation = ExecutionStepInformation.class))),
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
	@PostMapping(value = "/{stageName}/cancel",
	             consumes = {MediaType.ALL_VALUE},
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ExecutionStepInformation cancelProcess(
			@Parameter(description = "Step of which the process should be canceled.")
			@PathVariable final String stageName,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);

		final Stage stage = stepService.getStageConfiguration(stageName);

		final ExecutionStepEntity executionStep = processService.cancel(project, stage);
		return executionStepMapper.toDto(executionStep);
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
			@PathVariable final UUID processId,
			final MultipartHttpServletRequest request
	) throws ApiException {
		processService.finishProcess(processId, request.getFileMap().entrySet(), null);
		return ResponseEntity.ok().body(null);
	}

	@Operation(summary = "Callback endpoint for marking processes as failed.",
	           description = "Callback endpoint for marking processes as failed.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully marked the process as failed.",
			             content = @Content(schema = @Schema())),
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
	             consumes = MediaType.APPLICATION_JSON_VALUE,
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE}
	)
	public ResponseEntity<String> callbackJson(
			@Parameter(description = "Id of the process to mark as finished.")
			@PathVariable final UUID processId,
			@RequestBody final ErrorRequest errorRequest
	) throws ApiException {
		processService.finishProcess(processId, null, errorRequest);
		return ResponseEntity.ok().body(null);
	}

}
