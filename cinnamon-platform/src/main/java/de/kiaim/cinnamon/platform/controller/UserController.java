package de.kiaim.cinnamon.platform.controller;

import de.kiaim.cinnamon.model.spring.CustomMediaType;
import de.kiaim.cinnamon.platform.exception.InternalApplicationConfigurationException;
import de.kiaim.cinnamon.platform.model.dto.ErrorResponse;
import de.kiaim.cinnamon.platform.model.dto.RegisterRequest;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@Tag(name = "/api/user", description = "API for managing users.")
public class UserController {

	private final UserService userService;
	private final ProjectService projectService;

	@Autowired
	public UserController(final UserService userService, final ProjectService projectService) {
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
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = Boolean.class),
			                                 examples = {@ExampleObject("true")})}),
			@ApiResponse(responseCode = "500",
			             description = "User is not authorized.",
			             content = @Content),
	})
	@GetMapping(value = "/login",
	            produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public boolean login(
			@AuthenticationPrincipal UserEntity user
	) throws InternalApplicationConfigurationException {
		// TODO move somewhere else
		projectService.createProject(user);
		return true;
	}

	@Operation(summary = "Registers a new user.",
	           description = "Registers a new user.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully registered the new user.",
			             content = @Content),
			@ApiResponse(responseCode = "400",
			             description = "Invalid request. Email is not available or passwords do not match.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
	})
	@PostMapping(value = "/register",
	             consumes = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE},
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Object> register(
			@Parameter(description = "Information about the new user.",
			           content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE),
			           schema = @Schema(implementation = RegisterRequest.class))
			final @RequestBody @Valid RegisterRequest registerRequest
	) {
		userService.save(registerRequest.getEmail(), registerRequest.getPassword());
		return new ResponseEntity<>(HttpStatus.OK);
	}
}
