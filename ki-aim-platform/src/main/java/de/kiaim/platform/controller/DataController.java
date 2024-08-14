package de.kiaim.platform.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.model.data.DataRow;
import de.kiaim.model.data.DataSet;
import de.kiaim.model.spring.CustomMediaType;
import de.kiaim.platform.exception.*;
import de.kiaim.platform.model.dto.EstimateDataTypesRequest;
import de.kiaim.platform.model.dto.LoadDataRequest;
import de.kiaim.platform.model.dto.ReadDataRequest;
import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.model.file.FileConfiguration;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.dto.ErrorResponse;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.processor.DataProcessor;
import de.kiaim.platform.service.*;
import io.swagger.v3.oas.annotations.Operation;
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

	private final static String DATA_EXAMPLE = "[[true,\"2023-12-24\",\"2023-12-24T18:30:01.123456\",4.2,42,\"Hello World!\"]]";

	private final static String DATA_CONFIGURATION_EXAMPLE = """
			{"configurations":[{"index":0,"name":"column0_boolean","type":"BOOLEAN","scale":"NOMINAL","configurations":[]},{"index":1,"name":"column1_date","type":"DATE","scale":"DATE","configurations":[{"name":"DateFormatConfiguration","dateFormatter":"yyyy-MM-dd"},{"name":"RangeConfiguration","minValue":"1970-01-01","maxValue":"2030-01-01"}]},{"index":2,"name":"column2_date_time","type":"DATE_TIME","scale":"DATE","configurations":[{"name":"DateTimeFormatConfiguration","dateTimeFormatter":"yyyy-MM-dd'T'HH:mm:ss.SSSSSS"},{"name":"RangeConfiguration","minValue":"1970-01-01T00:01:00","maxValue":"2030-01-01T23:59:00"}]},{"index":3,"name":"column3_decimal","type":"DECIMAL","scale":"RATIO","configurations":[]},{"index":4,"name":"column4_integer","type":"INTEGER","scale":"INTERVAL","configurations":[{"name":"RangeConfiguration","minValue":0,"maxValue":100}]},{"index":5,"name":"column5_string","type":"STRING","scale":"NOMINAL","configurations":[{"name":"StringPatternConfiguration","pattern":".*"}]}]}""";

	private final static String DATA_SET_EXAMPLE = """
					{"dataConfiguration":""" + DATA_CONFIGURATION_EXAMPLE +
			"""
					,"data":""" + DATA_EXAMPLE + "}";

	private final static String TRANSFORMATION_RESULT_EXAMPLE =
			"""
					{"dataSet":""" + DATA_SET_EXAMPLE +
			"""
					,"transformationErrors": [
					{
					      "index": 1,
					      "rawValues": [
					        true,
					        "2023-12-24",
					        "",
					        4.2,
					        42,
					        "Hello World!"
					      ],
					      "dataTransformationErrors": [
					        {
					          "index": 2,
					          "errorType": "MISSING_VALUE"
					        }
					      ]
					    }
					]}
					""";

	private final DatabaseService databaseService;
	private final DataProcessorService dataProcessorService;
	private final DataSetService dataSetService;
	private final ProjectService projectService;
	private final StatusService statusService;
	private final UserService userService;

	@Autowired
	public DataController(final DatabaseService databaseService, final DataProcessorService dataProcessorService,
	                      final DataSetService dataSetService, final ProjectService projectService,
	                      final StatusService statusService, final UserService userService) {
		this.databaseService = databaseService;
		this.dataProcessorService = dataProcessorService;
		this.dataSetService = dataSetService;
		this.projectService = projectService;
		this.statusService = statusService;
		this.userService = userService;
	}

	@Operation(summary = "Estimates the data types of a given data set.",
	           description = "Estimates the data types of a given data set.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully estimated the data types. Returns the estimated data types.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = DataConfiguration.class),
			                                 examples = @ExampleObject(DATA_CONFIGURATION_EXAMPLE)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = DataConfiguration.class))}),
			@ApiResponse(responseCode = "400",
			             description = "The file is not supported or could not be read.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))})
	})
	@PostMapping(value = "/datatypes",
	             consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Object> estimateDatatypes(
			@ParameterObject @Valid final EstimateDataTypesRequest requestData,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.DATA_TYPES, requestData.getFile(), requestData.getFileConfiguration(), null,
		                     null, user);
	}

	@Operation(summary = "Converts and validates the uploaded file into a tabular representation.",
	           description = "Converts and validates the uploaded file into a tabular representation.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully converted and validated the data. Returns the result of the conversion.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = TransformationResult.class),
			                                 examples = @ExampleObject(TRANSFORMATION_RESULT_EXAMPLE)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = TransformationResult.class))}),
			@ApiResponse(responseCode = "400",
			             description = "The file is not supported or could not be read. The configuration is not valid.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))})
	})
	@PostMapping(value = "/validation",
	             consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
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
			                                 schema = @Schema(implementation = Long.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = Long.class))}),
			@ApiResponse(responseCode = "400",
			             description = "The file is not supported or could not be read. The configuration is not valid. The data has already been stored.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))})
	})
	@PostMapping(value = "/configuration",
	             consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Object> storeConfig(
			@ParameterObject @Valid final ReadDataRequest requestData,
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
			                                 schema = @Schema(implementation = Long.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = Long.class))}),
			@ApiResponse(responseCode = "400",
			             description = "The file is not supported or could not be read. The configuration is not valid. The data has already been stored.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))})
	})
	@PostMapping(value = "",
	             consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Object> storeData(
			@ParameterObject @Valid final ReadDataRequest requestData,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.STORE_DATE_SET, requestData.getFile(), requestData.getFileConfiguration(),
		                     requestData.getConfiguration(), null, user);
	}

	@Operation(summary = "Returns the configuration of the data set.",
	           description = "Returns the configuration of the data set.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully found the configuration. Returns the configuration of the data set.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = DataConfiguration.class),
			                                 examples = @ExampleObject(DATA_CONFIGURATION_EXAMPLE)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = DataConfiguration.class))}),
			@ApiResponse(responseCode = "400",
			             description = "The user has no stored configuration.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))})
	})
	@GetMapping(value = "/configuration",
	            produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Object> loadConfig(
			@AuthenticationPrincipal UserEntity user
	) throws ApiException, JsonProcessingException {
		return handleRequest(RequestType.LOAD_CONFIG, null, null, null, null, user);
	}

	@Operation(summary = "Returns the data of the data set.",
	           description = "Returns the data of the data.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully found the data and returns the data.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 array = @ArraySchema(schema = @Schema(implementation = DataRow.class)),
			                                 examples = {@ExampleObject(DATA_EXAMPLE)}),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 array = @ArraySchema(schema = @Schema(implementation = DataRow.class)),
			                                 examples = {
					                                 @ExampleObject("""
							                                 - - true
							                                   - "2023-12-24"
							                                   - "2023-12-24T18:30:01.123456"
							                                   - 4.2
							                                   - 42
							                                   - "Hello World!"
							                                   """)
			                                 })}),
			@ApiResponse(responseCode = "400",
			             description = "The user has no stored data.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))})
	})
	@GetMapping(value = "/data",
	            produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Object> loadData(
			@ParameterObject LoadDataRequest request,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.LOAD_DATA, null, null, null, request, user);
	}

	@Operation(summary = "Returns the data set.",
	           description = "Returns the data set.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully found the data set. Returns the data set.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = DataSet.class),
			                                 examples = @ExampleObject(DATA_SET_EXAMPLE)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = DataSet.class))}),
			@ApiResponse(responseCode = "400",
			             description = "The user has no stored data set.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))})
	})
	@GetMapping(value = "",
	            produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Object> loadDataSet(
			@ParameterObject LoadDataRequest request,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.LOAD_DATA_SET, null, null, null, request, user);
	}

	@Operation(summary = "Returns the transformation result.",
	           description = "Returns the transformation result.")
	@GetMapping(value = "/transformationResult",
	            produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Object> loadTransformationResult(
			@ParameterObject LoadDataRequest request,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.LOAD_TRANSFORMATION_RESULT, null, null, null, request, user);
	}

	@Operation(summary = "Deletes the data set from the internal data base.",
	           description = "Deletes the data set from the internal data base.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully deleted the data set.",
			             content = @Content),
			@ApiResponse(responseCode = "400",
			             description = "The user has no stored data set.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))})
	})
	@DeleteMapping(value = "",
	               produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
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
	 *     <li>{@link RequestType#LOAD_TRANSFORMATION_RESULT}: user</li>
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
	) throws ApiException {
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity projectEntity =  projectService.getProject(user);

		final List<String> columnNames = loadDataRequest != null && !loadDataRequest.getColumns().isBlank()
		                                 ? List.of(loadDataRequest.getColumns().split(","))
		                                 : new ArrayList<>();

		final Object result;
		switch (requestType) {
			case DATA_TYPES -> {
				final DataProcessor dataProcessor = dataProcessorService.getDataProcessor(file);
				final InputStream inputStream = getInputStream(file);
				result = dataProcessor.estimateDatatypes(inputStream, fileConfiguration);
				databaseService.store((DataConfiguration) result, projectEntity);
			}
			case DELETE -> {
				databaseService.delete(projectEntity);
				result = null;
			}
			case LOAD_CONFIG -> {
				result = databaseService.exportDataConfiguration(projectEntity);
			}
			case LOAD_DATA -> {
				final DataSet dataSet = databaseService.exportDataSet(projectEntity, columnNames);
				result = dataSetService.encodeDataRows(dataSet, projectEntity, loadDataRequest);
			}
			case LOAD_DATA_SET -> {
				result = databaseService.exportDataSet(projectEntity, columnNames);
			}
			case LOAD_TRANSFORMATION_RESULT -> {
				result = databaseService.exportTransformationResult(projectEntity);
			}
			case STORE_CONFIG -> {
				result = databaseService.store(configuration, projectEntity);
			}
			case STORE_DATE_SET -> {
				final DataProcessor dataProcessor = dataProcessorService.getDataProcessor(file);
				final InputStream inputStream = getInputStream(file);
				final TransformationResult transformationResult = dataProcessor.read(inputStream, fileConfiguration,
				                                                                     configuration);
				result = databaseService.store(transformationResult, projectEntity);

				statusService.updateCurrentStep(projectEntity, Step.ANONYMIZATION);
			}
			case VALIDATE -> {
				final DataProcessor dataProcessor = dataProcessorService.getDataProcessor(file);
				final InputStream inputStream = getInputStream(file);
				databaseService.store(configuration, projectEntity);
				result = dataProcessor.read(inputStream, fileConfiguration, configuration);
			}
			default -> {
				throw new InternalMissingHandlingException(InternalMissingHandlingException.REQUEST_TYPE,
				                                           "Missing handling for request type '" + requestType.name() +
				                                           "'");
			}
		}
		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	private InputStream getInputStream(final MultipartFile file) throws BadFileException {
		try {
			return file.getInputStream();
		} catch (IOException e) {
			throw new BadFileException(BadFileException.NOT_READABLE, "Could not read file");
		}
	}

	private enum RequestType {
		DATA_TYPES,
		DELETE,
		LOAD_CONFIG,
		LOAD_DATA,
		LOAD_DATA_SET,
		LOAD_TRANSFORMATION_RESULT,
		STORE_CONFIG,
		STORE_DATE_SET,
		VALIDATE;
	}

}
