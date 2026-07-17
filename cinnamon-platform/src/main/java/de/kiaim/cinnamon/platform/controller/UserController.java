package de.kiaim.cinnamon.platform.controller;

import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.model.dto.ErrorResponse;
import de.kiaim.cinnamon.platform.model.dto.ConfirmUserRequest;
import de.kiaim.cinnamon.platform.model.dto.ProjectInfo;
import de.kiaim.cinnamon.platform.model.dto.RegisterRequest;
import de.kiaim.cinnamon.platform.model.dto.UpdatePasswordRequest;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.platform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/user")
@Tag(name = "/api/user", description = "API for managing users.")
public class UserController {

	private final UserService userService;
	private final ProjectService projectService;

	@Autowired
	public UserController(final UserService userService, ProjectService projectService) {
		this.userService = userService;
		this.projectService = projectService;
	}

	@Operation(summary = "Check if the user credentials belong to an authorized user.",
	           description = "Check if the user credentials belong to an authorized user.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "User credential are correct.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = Boolean.class),
			                                 examples = {@ExampleObject("true")}),
			                        @Content(mediaType = MediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = Boolean.class),
			                                 examples = {@ExampleObject("true")})}),
			@ApiResponse(responseCode = "401",
			             description = "User is not authorized.",
			             content = @Content),
	})
	@GetMapping(value = "/login",
	            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public boolean login() {
		return true;
	}

	@Operation(summary = "Registers a new user.",
	           description = "Registers a new user.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully registered the new user.",
			             content = @Content),
			@ApiResponse(responseCode = "400",
			             description = "Invalid request. Username is not available or passwords do not match.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = MediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
	})
	@PostMapping(value = "/register",
	             consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE},
	             produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Object> register(
			@Parameter(description = "Information about the new user.",
			           content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE),
			           schema = @Schema(implementation = RegisterRequest.class))
			final @RequestBody @Valid RegisterRequest registerRequest
	) throws BadUserException {
		userService.register(registerRequest.getUsername(), registerRequest.getPassword());
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@Operation(summary="Deletes the currently authenticated user.",
	           description="Deletes the currently authenticated user.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully deleted the user.",
			             content = @Content),
			@ApiResponse(responseCode = "400",
			             description = "If the user has a project with a running process.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = MediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
			@ApiResponse(responseCode = "403",
			             description = "The credentials do not match the authenticated user.",
			             content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred when deleting the user.",
			             content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))),
	})
	@DeleteMapping(value = "/-/delete",
	               consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
	public void delete(
			@ParameterObject @Valid final ConfirmUserRequest confirmUserRequest,
			@AuthenticationPrincipal final UserEntity user)
			throws BadDataSetIdException, BadStateException, BadUserConfirmationException,
					       InternalDataSetPersistenceException, InternalInvalidStateException {
		userService.confirmUser(confirmUserRequest, user);
		userService.deleteUser(userService.getUserByUsername(user.getUsername()));
	}


	@PostMapping(value = "/-/update-password",
	             consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
	public void updatePassword(
			@ParameterObject @Valid final UpdatePasswordRequest updatePasswordRequest,
			@AuthenticationPrincipal final UserEntity user
	) throws ApiException {
		userService.updatePassword(user.getUsername(), updatePasswordRequest.getCurrentPassword(),
		                           updatePasswordRequest.getNewPassword());
	}

	@Operation(summary = "Returns all projects of the currently authenticated user.")
	@GetMapping(value = "/-/projects",
	            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public Set<ProjectInfo> getProjects(
			@AuthenticationPrincipal final UserEntity user
	) throws ApiException {
		return userService.getProjects(user.getUsername());
	}

	@Operation(summary = "Creates a new project for the currently authenticated user.")
	@PostMapping(value = "/-/projects",
	             consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
	             produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public ProjectInfo createProject(
			@RequestParam final String projectName,
			@AuthenticationPrincipal final UserEntity user
	) throws ApiException {
		final ProjectEntity project = userService.createProject(user.getUsername(), projectName, null);
		return projectService.getProjectInfo(project);
	}

}
