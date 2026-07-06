package de.kiaim.cinnamon.platform.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import de.kiaim.cinnamon.model.configuration.algorithms.AlgorithmDefinition;
import de.kiaim.cinnamon.model.configuration.algorithms.AvailableAlgorithms;
import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.model.configuration.data.file.FileType;
import de.kiaim.cinnamon.model.configuration.project.ProjectConfigurationDTO;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.model.dto.ConfigurationImportSummary;
import de.kiaim.cinnamon.platform.config.SerializationConfig;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.dto.*;
import de.kiaim.cinnamon.model.dto.ErrorResponse;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.model.enumeration.HoldOutSelector;
import de.kiaim.cinnamon.platform.service.*;
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

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
@Tag(name = "/api/config", description = "API for managing configurations. " +
                                         "Configurations are associated with the user of the request.")
public class ConfigurationController {

	private final ConfigurationService configurationService;
	private final DatabaseService databaseService;
	private final DataProcessorService dataProcessorService;
	private final ExternalConfigurationService externalConfigurationService;
	private final ProjectService projectService;
	private final UserService userService;
	private final ObjectMapper yamlMapper;

	public ConfigurationController(final ConfigurationService configurationService,
	                               final DatabaseService databaseService,
	                               final DataProcessorService dataProcessorService,
	                               final ExternalConfigurationService externalConfigurationService,
	                               final SerializationConfig serializationConfig,
	                               final ProjectService projectService,
	                               final UserService userService) {
		this.configurationService = configurationService;
		this.databaseService = databaseService;
		this.dataProcessorService = dataProcessorService;
		this.externalConfigurationService = externalConfigurationService;
		this.yamlMapper = serializationConfig.yamlMapper();
		this.projectService = projectService;
		this.userService = userService;
	}

	@Operation(summary = "Returns general information about the given configuration.",
	           description = "Returns general information about the given configuration.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Returns the information.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ConfigurationInfo.class))),
			@ApiResponse(responseCode = "400",
			             description = "The configuration name is invalid.",
			             content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500",
			             description = "The application state is invalid.",
			             content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping(value = "/info", produces = MediaType.APPLICATION_JSON_VALUE)
	public ConfigurationInfo info(
			@Parameter(description = "Name of the configuration.",
			           content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE,
			                              schema = @Schema(implementation = String.class)),
			           required = true)
			@RequestParam("name") final String configurationName,
			@AuthenticationPrincipal UserEntity requestUser
	) throws BadConfigurationNameException, InternalInvalidStateException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);
		return externalConfigurationService.getInfo(configurationName, project);
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
	             produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public void store(
			@Valid @ParameterObject ConfigurationRequest configurationRequest,
			@AuthenticationPrincipal UserEntity requestUser
	) throws BadAlgorithmException, BadConfigurationFileException, BadConfigurationNameException, BadStateException, InternalIOException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);
		configurationService.importExternalConfiguration(project, configurationRequest.getConfiguration());
	}

	@Operation(summary = "Imports all configurations defined in the given file.",
	           description = "Imports all configurations defined in the given file.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully imported the configurations.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ConfigurationImportSummary.class)),
			                        @Content(mediaType = MediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ConfigurationImportSummary.class))}),
			@ApiResponse(responseCode = "400",
			             description = "The given configuration file is invalid.",
			             content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
	})
	@PostMapping(value = "/import",
	             consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
	             produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public ConfigurationImportSummary importConfigurations(
			@Valid @ParameterObject ImportConfigurationRequest importConfigurationRequest,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws BadFileException, BadConfigurationFileException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);
		return configurationService.importConfigurations(project, importConfigurationRequest.getConfiguration(),
		                                                 importConfigurationRequest.getImportParameters());
	}

	@Operation(summary = "Loads a previously stored configuration with the given name.",
	           description = "Loads a previously stored configuration with the given name.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully loaded the configuration. Returns the content of the configuration",
			             content = {
					             @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					                      schema = @Schema(implementation = ProjectConfigurationDTO.class)),
					             @Content(mediaType = MediaType.APPLICATION_YAML_VALUE,
					                      schema = @Schema(implementation = ProjectConfigurationDTO.class)),
					             @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					                      schema = @Schema(implementation = DataConfiguration.class)),
					             @Content(mediaType = MediaType.APPLICATION_YAML_VALUE,
					                      schema = @Schema(implementation = DataConfiguration.class)),
			}),
			@ApiResponse(responseCode = "400",
			             description = "The user has no stored configurations or no configuration with the give name.",
			             content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
	})
	@GetMapping(value = "",
	            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public Object load(
			@Parameter(description = "Name of the configuration to be loaded.",
			           content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE,
			                              schema = @Schema(implementation = String.class)),
			           required = true)
			@RequestParam(name = "name") final String configurationName,
			@AuthenticationPrincipal UserEntity requestUser
	) throws BadStateException, InternalIOException, BadConfigurationNameException, InternalInvalidStateException {
		// Load user from the database because lazy loaded fields cannot be read from the injected user
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);
		return configurationService.loadConfiguration(configurationName, project);
	}

	@Operation(summary = "Loads available algorithm from the server corresponding to the given configuration name.",
	           description = "Loads available algorithm from the server corresponding to the given configuration name.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully returns the available algorithms.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = AvailableAlgorithms.class)),
			                        @Content(mediaType = MediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = AvailableAlgorithms.class))}),
			@ApiResponse(responseCode = "400",
			             description = "The given configuration name is invalid.",
			             content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500",
			             description = "The request to the external server failed.",
			             content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
	})
	@GetMapping(value = "/algorithms", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public AvailableAlgorithms getAvailableAlgorithms(
			@Valid final AvailableAlgorithmsRequest request
	) throws InternalRequestException, BadConfigurationNameException {
		return externalConfigurationService.fetchAvailableAlgorithms(request.getConfigurationName());
	}

	@Operation(summary = "Loads the configuration definition for the given algorithm.",
	           description = "Loads the configuration definition for the given algorithm.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully returns the configuration definition.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = AlgorithmDefinition.class)),
			                        @Content(mediaType = MediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = AlgorithmDefinition.class))
			}),
			@ApiResponse(responseCode = "400",
			             description = "The given configuration name is invalid.",
			             content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500",
			             description = "The request to the external server failed.",
			             content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
	})
	@GetMapping(value = "/algorithm", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_YAML_VALUE})
	public AlgorithmDefinition getAlgorithmDefinition(
			@Valid final AlgorithmDefinitionRequest request,
			@AuthenticationPrincipal UserEntity requestUser
	) throws BadConfigurationNameException, InternalDataSetPersistenceException, InternalInvalidStateException, InternalRequestException {
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);
		return externalConfigurationService.fetchAlgorithmDefinition(project, request.getConfigurationName(),
		                                                             request.getDefinitionPath());
	}

	@PostMapping(value = "/synthetization/named-list/{listName}/suggest",
	             consumes = MediaType.APPLICATION_JSON_VALUE,
	             produces = MediaType.APPLICATION_JSON_VALUE)
	public JsonNode suggestNamedList(
			@PathVariable("listName") final String listName,
			@RequestBody final Map<String, Object> algorithmValues,
			@AuthenticationPrincipal UserEntity requestUser
	) throws ApiException {
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity project = projectService.getProject(user);

		if (project.getOriginalData().getDataSet() == null) {
			throw new BadStateException(BadStateException.NO_DATA_SET, "No original data set is present.");
		}

		final DataSet dataSet = databaseService.exportDataSet(project.getOriginalData().getDataSet(), HoldOutSelector.ALL);
		final DataConfiguration dataConfiguration = dataSet.getDataConfiguration();

		final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		dataProcessorService.getDataProcessor(FileType.CSV).write(outputStream, dataSet);

		final Map<String, Object> algorithm = new HashMap<>(algorithmValues);
		algorithm.putIfAbsent("synthesizer", resolveSynthesizerForNamedList(listName));
		final Map<String, Object> wrappedConfiguration = Map.of(
				"synthetization_configuration",
				Map.of("algorithm", algorithm)
		);

		final String attributeConfigurationYaml;
		final String algorithmConfigurationYaml;
		try {
			attributeConfigurationYaml = yamlMapper.writeValueAsString(dataConfiguration);
			algorithmConfigurationYaml = yamlMapper.writeValueAsString(wrappedConfiguration);
		} catch (final JsonProcessingException e) {
			throw new InternalInvalidStateException(
					InternalInvalidStateException.INVALID_CONFIGURATION,
					"Failed to serialize the named-list suggestion request.",
					e
			);
		}

		return externalConfigurationService.suggestNamedList(
				listName,
				attributeConfigurationYaml,
				algorithmConfigurationYaml,
				outputStream.toByteArray()
		);
	}

	private String resolveSynthesizerForNamedList(final String listName) throws BadStateException {
		return switch (listName) {
			case "required_attributes" -> "llm_text_only_semantic_variation_synthesis";
			default -> throw new BadStateException(
					BadStateException.CONFIGURATION,
					"Unsupported named list suggestion target: " + listName
			);
		};
	}

}
