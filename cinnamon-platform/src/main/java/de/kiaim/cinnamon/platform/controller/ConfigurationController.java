package de.kiaim.cinnamon.platform.controller;

import de.kiaim.cinnamon.model.spring.CustomMediaType;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.dto.ConfigurationRequest;
import de.kiaim.cinnamon.platform.model.dto.ErrorResponse;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.service.DatabaseService;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.platform.service.UserService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config")
@Tag(name = "/api/config", description = "API for managing configurations. " +
                                         "Configurations are associated with the user of the request.")
public class ConfigurationController {

	private final DatabaseService databaseService;
	private final ProjectService projectService;
	private final UserService userService;

	public ConfigurationController(final DatabaseService databaseService, final ProjectService projectService,
	                               final UserService userService) {
		this.databaseService = databaseService;
		this.projectService = projectService;
		this.userService = userService;
	}

	@Operation(summary = "Stores any configuration under the given name.",
	           description = "Stores any configuration under the given name. An existing configuration with the same name will be overwritten.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully stored the configuration.",
			             content = @Content)
	})
	@PostMapping(value = "",
	             consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public void store(
			@Valid @ParameterObject ConfigurationRequest configurationRequest,
			@AuthenticationPrincipal UserEntity requestUser
	) throws BadConfigurationNameException, BadStateException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);
		databaseService.storeConfiguration(configurationRequest.getConfigurationName(), configurationRequest.getUrl(),
		                                   configurationRequest.getConfiguration(), project);
	}

	@Operation(summary = "Loads a previously stored configuration with the given name.",
	           description = "Loads a previously stored configuration with the given name.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully loaded the configuration. Returns the content of the configuration",
			             content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE,
			                                schema = @Schema(implementation = String.class))),
			@ApiResponse(responseCode = "400",
			             description = "The user has no stored configurations or no configuration with the give name.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
	})
	@GetMapping(value = "",
	            produces = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE,
	                        CustomMediaType.APPLICATION_YAML_VALUE})
	public String load(
			@Parameter(description = "Name of the configuration to be loaded.",
			           content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE,
			                              schema = @Schema(implementation = String.class)),
			           required = true)
			@RequestParam(name = "name") final String configurationName,
			@AuthenticationPrincipal UserEntity requestUser
	) throws BadConfigurationNameException, BadDataSetIdException, BadStepNameException, InternalApplicationConfigurationException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);
		return databaseService.exportConfiguration(configurationName, project);
	}

}
