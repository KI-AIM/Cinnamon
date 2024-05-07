package de.kiaim.platform.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.platform.config.YamlMapper;
import de.kiaim.platform.exception.*;
import de.kiaim.platform.model.DataSet;
import de.kiaim.platform.model.dto.LoadDataRequest;
import de.kiaim.platform.model.dto.ReadDataRequest;
import de.kiaim.platform.model.dto.StoreDataConfigurationRequest;
import de.kiaim.platform.model.file.FileConfiguration;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.data.DataRow;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.model.dto.ErrorResponse;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.processor.CsvProcessor;
import de.kiaim.platform.processor.DataProcessor;
import de.kiaim.platform.service.DataSetService;
import de.kiaim.platform.service.DatabaseService;
import de.kiaim.platform.service.ResponseService;
import de.kiaim.platform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

// TODO Support different languages
@RestController
@RequestMapping("/api/data")
@Tag(name = "/api/data", description = "API for managing data sets. " +
                                       "Supports CSV files. " +
                                       "Data Sets are associated with the user of the request.")
public class DataController {

	private final ObjectMapper yamlMapper;
	// TODO Find Processor dynamically
	private final CsvProcessor csvProcessor;
	private final DatabaseService databaseService;
	private final DataSetService dataSetService;
	private final UserService userService;
	private final ResponseService responseService;

	@Autowired
	public DataController(final CsvProcessor csvProcessor, final DatabaseService databaseService,
	                      final DataSetService dataSetService, final UserService userService,
	                      final ResponseService responseService) {
		this.csvProcessor = csvProcessor;
		this.databaseService = databaseService;
		this.dataSetService = dataSetService;
		this.userService = userService;
		this.responseService = responseService;
		this.yamlMapper = YamlMapper.yamlMapper();
	}

	@Operation(summary = "Estimates the data types of a given data set.",
	           description = "Estimates the data types of a given data set.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully estimated the data types. Returns the estimated data types.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = DataConfiguration.class))}),
			@ApiResponse(responseCode = "400",
			             description = "The file is not supported or could not be read.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PostMapping(value = "/datatypes",
	             consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
	             produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> estimateDatatypes(
			@Parameter(description = "File containing the data.",
			           content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
			@RequestPart(value = "file") MultipartFile file,
			@Parameter(description = "Configuration for the file.",
			           content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                              schema = @Schema(implementation = FileConfiguration.class)))
			@RequestParam("fileConfiguration") FileConfiguration fileConfiguration,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.DATA_TYPES, file, fileConfiguration ,null, null, user);
	}

	@Operation(summary = "Converts and validates the uploaded file into a tabular representation.",
	           description = "Converts and validates the uploaded file into a tabular representation.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully converted and validated the data. Returns the result of the conversion.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = TransformationResult.class))}),
			@ApiResponse(responseCode = "400",
			             description = "The file is not supported or could not be read. The configuration is not valid.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PostMapping(value = "/validation",
	             consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
	             produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> readAndValidateData(
			@ParameterObject @Valid final ReadDataRequest requestData,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.VALIDATE, requestData.getFile(), requestData.getFileConfiguration(),
		                     requestData.getConfiguration(), null, user);
	}

	@Operation(summary = "Stores or updates the given configuration.",
	           description = "Stores or updates the given configuration. The configuration can be a JSON or YAML string.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully stored the configuration. Returns the id of the data set.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = Long.class))}),
			@ApiResponse(responseCode = "400",
			             description = "The file is not supported or could not be read. The configuration is not valid. The data has already been stored.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PostMapping(value = "/configuration",
	             consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
	             produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> storeConfig(
			@ParameterObject @Valid final StoreDataConfigurationRequest requestData,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.STORE_CONFIG, null, null, requestData.getConfiguration(), null, user);
	}

	@Operation(summary = "Stores the given data into the internal database for further processing.",
	           description = "Stores the given data into the internal database for further processing.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully stored the data. Returns the id of the data set.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = Long.class))}),
			@ApiResponse(responseCode = "400",
			             description = "The file is not supported or could not be read. The configuration is not valid. The data has already been stored.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PostMapping(value = "",
	             consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
	             produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> storeData(
			@ParameterObject @Valid final ReadDataRequest requestData,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.STORE_DATE_SET, requestData.getFile(), requestData.getFileConfiguration(),
		                     requestData.getConfiguration(), null, user);
	}

	@Operation(summary = "Returns the configuration of the data set.",
	           description = "Returns the configuration of the data set. Available formats are JSON and YAML")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully found the configuration. Returns the configuration of the data set.",
			             content = {
					             @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					                      schema = @Schema(implementation = DataConfiguration.class)),
					             @Content(mediaType = "application/x-yaml",
					                      schema = @Schema(implementation = DataConfiguration.class)),
			             }),
			@ApiResponse(responseCode = "400",
			             description = "The user has no stored configuration.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping(value = "/configuration",
	            produces = {MediaType.APPLICATION_JSON_VALUE, "application/x-yaml"})
	public ResponseEntity<Object> loadConfig(
			@Parameter(description = "Output format of the configuration. Allowed are 'json' and 'yaml'. If the value empty of invalid, json will be returned.",
			           content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                              schema = @Schema(implementation = String.class)))
			@RequestParam(defaultValue = "json") String format,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException, JsonProcessingException {
		ResponseEntity<Object> response = handleRequest(RequestType.LOAD_CONFIG, null, null, null, null, user);

		if (response.getStatusCode().is2xxSuccessful() && format.equals("yaml")) {
			final String yaml = yamlMapper.writeValueAsString(response.getBody());
			response = ResponseEntity.ok().header("Content-Type", "application/x-yaml").body(yaml);
		}

		return response;
	}

	@Operation(summary = "Returns the data of the data set.",
	           description = "Returns the data of the data.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully found the data and returns the data.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 array = @ArraySchema(schema = @Schema(implementation = DataRow.class)),
			                                 examples = {
					                                 @ExampleObject(
							                                 "[[true, \"2023-12-24\", \"2023-12-24T18:30:01.123456\", 4.2, 42, \"Hello World!\"]]")
			                                 })
			             }),
			@ApiResponse(responseCode = "400",
			             description = "The user has no stored data.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping(value = "/data",
	            produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> loadData(
			@ParameterObject LoadDataRequest request,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.LOAD_DATA, null, null, null,  request, user);
	}

	@Operation(summary = "Returns the data set.",
	           description = "Returns the data set.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully found the data set. Returns the data set.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = DataSet.class))}),
			@ApiResponse(responseCode = "400",
			             description = "The user has no stored data set.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping(value = "",
	            produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> loadDataSet(
			@ParameterObject LoadDataRequest request,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.LOAD_DATA_SET, null, null, null, request, user);
	}

	@Operation(summary = "Deletes the data set from the internal data base.",
	           description = "Deletes the data set from the internal data base.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully deleted the data set.",
			             content = @Content),
			@ApiResponse(responseCode = "400",
			             description = "The user has no stored data set.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class)))
	})
	@DeleteMapping(value = "",
	               produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> deleteData(
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.DELETE, null, null, null, null, user);
	}

	/**
	 * Handles a request of the given type.
	 * For each RequestType, different attributes must not be null:
	 * <ul>
	 *     <li>{@link RequestType#DATA_TYPES}: file, fileConfiguration</li>
	 *     <li>{@link RequestType#DELETE}: user</li>
	 *     <li>{@link RequestType#LOAD_CONFIG}: user</li>
	 *     <li>{@link RequestType#LOAD_DATA}: loadDataRequest, user</li>
	 *     <li>{@link RequestType#LOAD_DATA_SET}: loadDataRequest, user</li>
	 *     <li>{@link RequestType#STORE_CONFIG}: configuration, user</li>
	 *     <li>{@link RequestType#STORE_DATE_SET}: file, fileConfiguration, configuration, user</li>
	 *     <li>{@link RequestType#VALIDATE}: file, fileConfiguration, configuration</li>
	 * </ul>
	 *
	 * @param requestType       Type of the request.
	 * @param file              File containing the source data.
	 * @param fileConfiguration Configuration describing the file.
	 * @param configuration     Configuration describing the source data.
	 * @param loadDataRequest   Settings for the data set export.
	 * @param requestUser       User of the request.
	 * @return Response entity containing the response based on the request type or an error description.
	 */
	private ResponseEntity<Object> handleRequest(
			final RequestType requestType,
			@Nullable final MultipartFile file,
			@Nullable final FileConfiguration fileConfiguration,
			@Nullable final DataConfiguration configuration,
			@Nullable final LoadDataRequest loadDataRequest,
			final UserEntity requestUser
	) throws BadColumnNameException, BadDataSetIdException, BadFileException, InternalDataSetPersistenceException {
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());

		final List<String> columnNames = loadDataRequest != null && !loadDataRequest.getColumns().isBlank()
		                                 ? List.of(loadDataRequest.getColumns().split(","))
		                                 : new ArrayList<>();

		final Object result;
		switch (requestType) {
			case DATA_TYPES -> {
				final DataProcessor dataProcessor = getDataProcessor(file);
				final InputStream inputStream = getInputStream(file);
				result = dataProcessor.estimateDatatypes(inputStream, fileConfiguration);
				databaseService.store((DataConfiguration) result, user);
			}
			case DELETE -> {
				databaseService.delete(user);
				result = null;
			}
			case LOAD_CONFIG -> {
				result = databaseService.exportDataConfiguration(user);
			}
			case LOAD_DATA -> {
				final DataSet dataSet = databaseService.exportDataSet(user, columnNames);
				result = dataSetService.encodeDataRows(dataSet, user.getPlatformConfiguration(), loadDataRequest);
			}
			case LOAD_DATA_SET -> {
				result = databaseService.exportDataSet(user, columnNames);
			}
			case STORE_CONFIG -> {
				result = databaseService.store(configuration, user);
			}
			case STORE_DATE_SET -> {
				final DataProcessor dataProcessor = getDataProcessor(file);
				final InputStream inputStream = getInputStream(file);
				final TransformationResult transformationResult = dataProcessor.read(inputStream, fileConfiguration, configuration);
				result = databaseService.store(transformationResult, user);
			}
			case VALIDATE -> {
				final DataProcessor dataProcessor = getDataProcessor(file);
				final InputStream inputStream = getInputStream(file);
				databaseService.store(configuration, user);
				result = dataProcessor.read(inputStream, fileConfiguration, configuration);
			}
			default -> {
				return responseService.prepareErrorResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR,
				                                                  "Missing handling for request type '" +
				                                                  requestType.name() + "'");
			}
		}
		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	private DataProcessor getDataProcessor(final MultipartFile file) throws BadFileException {
		final String fileExtension = extractFileExtension(file);

		switch (fileExtension) {
			case ".csv":
				return csvProcessor;
			default:
				throw new BadFileException("Unsupported file type: '" + fileExtension + "'");
		}
	}

	private InputStream getInputStream(final MultipartFile file) throws BadFileException {
		try {
			return file.getInputStream();
		} catch (IOException e) {
			throw new BadFileException("Could not read file");
		}
	}

	private String extractFileExtension(final MultipartFile file) throws BadFileException {
		if (file == null) {
			throw new BadFileException("Missing file");
		}

		final String fileName = file.getOriginalFilename();
		if (fileName == null || fileName.isBlank()) {
			throw new BadFileException("Missing filename");
		}

		final int fileExtensionBegin = file.getOriginalFilename().lastIndexOf('.');
		if (fileExtensionBegin == -1) {
			throw new BadFileException("Missing file extension");
		}

		return file.getOriginalFilename().substring(fileExtensionBegin);
	}

	private enum RequestType {
		DATA_TYPES,
		DELETE,
		LOAD_CONFIG,
		LOAD_DATA,
		LOAD_DATA_SET,
		STORE_CONFIG,
		STORE_DATE_SET,
		VALIDATE;
	}

}
