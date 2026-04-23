package de.kiaim.cinnamon.platform.controller;

import de.kiaim.cinnamon.model.dto.ErrorResponse;
import de.kiaim.cinnamon.platform.service.WorkflowService;
import de.kiaim.cinnamon.platform.exception.ApiException;
import de.kiaim.cinnamon.platform.model.dto.WorkflowInformation;
import de.kiaim.cinnamon.platform.model.dto.WorkflowRequest;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

/**
 * Endpoint for interacting with workflows.
 * A workflow is a wrapper for executing the entire anonymization pipeline including the evaluation.
 * They are separated from normal projects and cannot be accessed through the other endpoints.
 * Workflows can be started by posting to {@code /api/workflow} with a {@link WorkflowRequest} containing the data and configuration files.
 * This returns the {@code workflowId} of the started workflow together with the current status.
 * The client can observe the current status of the workflow by polling the {@code /api/workflow/{workflowId}} endpoint.
 * The results can be downloaded by deleting the workflow with {@code DELETE /api/workflow/{workflowId}}.
 *
 * @author Daniel Preciado-Marquez
 */
@RestController
@RequestMapping("/api/workflow")
@Validated
@Tag(name = "/api/worklfow", description = "API for managing workflows.")
public class WorkflowController {

	private final WorkflowService workflowService;

	public WorkflowController(final WorkflowService workflowService) {
		this.workflowService = workflowService;
	}

	@Operation(summary = "Start a new workflow.",
	           description = "Starts a new workflow with the given data and configuration. Returns the workflowId and the current status of the workflow.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "202", description = "Workflow started successfully."),
			@ApiResponse(responseCode = "400",
			             description = "The configuration file or the data is invalid.",
			             content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred when starting the workflow.",
			             content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
	})
	@PostMapping(value = "",
	             consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
	             produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	@ResponseStatus(value = HttpStatus.ACCEPTED)
	public WorkflowInformation postNewWorkflow(
			@ModelAttribute @Valid final WorkflowRequest workflowRequest,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		final UUID workflowId = workflowService.startWorkflow(requestUser.getEmail(), workflowRequest.getData(),
		                                                      workflowRequest.getConfiguration());
		return workflowService.getWorkflowStatus(requestUser.getEmail(), workflowId);
	}

	@Operation(summary = "Get the status of a workflow.",
	           description = "Returns the current status of the workflow with the given workflowId.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Workflow status retrieved successfully."),
			@ApiResponse(responseCode = "404",
			             description = "No workflow with the given workflowId was found.",
			             content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500",
			             description = "Fetching the status of the currently running process from an external module failed.",
			             content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
	})
	@GetMapping(value = "{workflowId}",
	            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public WorkflowInformation getWorkflow(
			@AuthenticationPrincipal final UserEntity requestUser,
			@Parameter(description = "The ID of the workflow.", required = true) @PathVariable final String workflowId
	) throws ApiException {
		return workflowService.getWorkflowStatus(requestUser.getEmail(), workflowId);
	}

	@Operation(summary = "Deletes a workflow and returns the project export.",
	           description = "Deletes a workflow and returns the project export.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Workflow deleted successfully"),
			@ApiResponse(responseCode = "404",
			             description = "No workflow with the given workflowId was found.",
			             content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500",
			             description = "Deleting the workflow failed.",
			             content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
	})
	@DeleteMapping(value = "{workflowId}",
	               produces = {MediaType.APPLICATION_OCTET_STREAM_VALUE})
	public ResponseEntity<StreamingResponseBody> deleteWorkflow(
			@AuthenticationPrincipal final UserEntity requestUser,
			@Parameter(description = "The ID of the workflow.", required = true) @PathVariable final String workflowId,
			final HttpServletResponse response
	) throws ApiException {
		return workflowService.deleteWorkflow(requestUser.getEmail(), workflowId, response);
	}
}
