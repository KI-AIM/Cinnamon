package de.kiaim.platform.controller;

import de.kiaim.platform.model.dto.ErrorResponse;
import de.kiaim.platform.model.dto.RegisterRequest;
import de.kiaim.platform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@Tag(name = "/api/user", description = "API for managing users.")
public class UserController {

	private final UserService userService;

	@Autowired
	public UserController(UserService userService) {
		this.userService = userService;
	}

	@Operation(summary = "Registers a new user.",
	           description = "Registers a new user.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully registered the new user.",
			             content = @Content),
			@ApiResponse(responseCode = "400",
			             description = "Invalid request. Email is not available or passwords do not match.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
	})
	@PostMapping(value = "/register",
	             consumes = MediaType.APPLICATION_JSON_VALUE,
	             produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> register(
			@Parameter(description = "Information about the new user.",
			           content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE),
			           schema = @Schema(implementation = RegisterRequest.class))
			final @RequestBody @Valid RegisterRequest registerRequest
	) {
		userService.save(registerRequest.getEmail(), registerRequest.getEmail());
		return new ResponseEntity<>(HttpStatus.OK);
	}
}
