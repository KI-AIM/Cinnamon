package de.kiaim.platform.controller;

import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.model.data.DataRow;
import de.kiaim.model.data.DataSet;
import de.kiaim.model.spring.CustomMediaType;
import de.kiaim.platform.exception.*;
import de.kiaim.platform.model.dto.*;
import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.platform.model.enumeration.RowSelector;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.processor.DataProcessor;
import de.kiaim.platform.service.*;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
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

	@Operation(summary = "Returns information about the uploaded file.",
	           description = "Returns information about the uploaded file.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Returns the estimated data configuration.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = FileInformation.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = FileInformation.class))}),
	})
	@GetMapping(value = "/file",
	            produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<FileInformation> getFile(
			@AuthenticationPrincipal final UserEntity requestUser
	) {
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity projectEntity =  projectService.getProject(user);
		final var fileInformation = databaseService.getFileInformation(projectEntity);
		return ResponseEntity.ok(fileInformation);
	}

	@Operation(summary = "Stores the given file and file configuration.",
	           description = "Stores the given file and file configuration.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully stored the file and the file configuration.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = FileInformation.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = FileInformation.class))}),
			@ApiResponse(responseCode = "400",
			             description = "The file is not supported or could not be read or the dataset has already been confirmed.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))}),
			@ApiResponse(responseCode = "500",
			             description = "The dataset has been stored but not confirmed and could not be deleted.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = ErrorResponse.class))})
	})
	@PostMapping(value = "/file",
	             consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<FileInformation> uploadFile(
			@ParameterObject @Valid final UploadFileRequest requestData,
			@AuthenticationPrincipal final UserEntity requestUser
	) throws ApiException {
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity projectEntity =  projectService.getProject(user);

		final FileInformation fileInformation = databaseService.storeFile(projectEntity, requestData.getFile(),
		                                                                  requestData.getFileConfiguration());
		statusService.updateCurrentStep(projectEntity, Step.DATA_CONFIG);
		return ResponseEntity.ok(fileInformation);
	}

	@Operation(summary = "Estimates the data configuration of a given data set.",
	           description = "Estimates the data configuration of a given data set.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully estimated the data configuration. Returns the estimated data configuration.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = DataConfiguration.class),
			                                 examples = @ExampleObject(DATA_CONFIGURATION_EXAMPLE)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = DataConfiguration.class))}),
			@ApiResponse(responseCode = "400",
			             description = "The data set has already been confirmed.",
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
	@Transactional
	@GetMapping(value = "/estimation",
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Object> estimateDatatypes(
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.ESTIMATE, null, null, null, user);
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
			             description = "The file is not supported or could not be read. The configuration is not valid. The data has already been confirmed.",
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
			@Valid final StoreDataConfigurationRequest request,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.STORE_CONFIG, request.getConfiguration(), null, null, user);
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
	@Transactional
	@PostMapping(value = "",
	             consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Object> storeData(
			@Valid final StoreDataConfigurationRequest request,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.STORE_DATE_SET, request.getConfiguration(), null, null, user);
	}

	@Operation(summary = "Confirms that the current dataset should be used.",
	           description = "Confirms that the current dataset should be used.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully confirmed the data set.",
			             content = {@Content()}),
			@ApiResponse(responseCode = "400",
			             description = "The data set has not been stored..",
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
	@PostMapping(value = "/confirm",
	             consumes = MediaType.ALL_VALUE,
	             produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Object> confirmData(
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.CONFIRM_DATE_SET, null, null, null, user);
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
			@AuthenticationPrincipal final UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.LOAD_CONFIG, null, null, null, user);
	}

	@Operation(summary = "Returns general information the data set.",
	           description = "Returns general information the data set.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Returns the general information of the data set.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = DataSetInfo.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = DataSetInfo.class))}),
			@ApiResponse(responseCode = "400",
			             description = "The data set does not exist.",
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
	@GetMapping(value = "/{stepName}/info",
	            produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Object> info(
			@Parameter(description = "Step the requested data belongs to.")
			@PathVariable final String stepName,
			@AuthenticationPrincipal final UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.INFO, null, stepName, null, user);
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
	@GetMapping(value = "/{stepName}/data",
	            produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Object> loadData(
			@Parameter(description = "Step the requested data belongs to.")
			@PathVariable final String stepName,
			@ParameterObject LoadDataRequest request,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.LOAD_DATA, null, stepName, request, user);
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
	@GetMapping(value = "/{stepName}",
	            produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Object> loadDataSet(
			@Parameter(description = "Step the requested data belongs to.")
			@PathVariable final String stepName,
			@ParameterObject LoadDataRequest request,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.LOAD_DATA_SET, null, stepName, request, user);
	}

	@Operation(summary = "Returns the entire transformation result.",
	           description = "Returns the entire transformation result.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully found the data and returns the data.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = TransformationResult.class)
			             ),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = TransformationResult.class)
			                        )}),
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
	@GetMapping(value = "/{stepName}/transformationResult",
	            produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Object> loadTransformationResult(
			@Parameter(description = "Step the requested data belongs to.")
			@PathVariable final String stepName,
			@ParameterObject LoadDataRequest request,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.LOAD_TRANSFORMATION_RESULT, null, stepName, request, user);
	}

	@Operation(summary = "Returns a page the transformation result.",
	           description = "Returns the page of the transformation result.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully returns the page.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = TransformationResultPage.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = TransformationResultPage.class)
			                        )}),
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
	@GetMapping(value = "/{stepName}/transformationResult/page",
	            produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Object> loadTransformationResultPage(
			@Parameter(description = "Step the requested data belongs to.")
			@PathVariable final String stepName,
			@Parameter(description = "Page number starting at 1.")
			@RequestParam(required = true) final Integer page,
			@Parameter(description = "Number of items per page.")
			@RequestParam(required = true) final Integer perPage,
			@Parameter(description = "Selector for the rows to be included.")
			@RequestParam(required = false, defaultValue = "ALL") final RowSelector rowSelector,
			@ParameterObject final LoadDataRequest request,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.LOAD_TRANSFORMATION_RESULT_PAGE, null, stepName, request, user, page, perPage, rowSelector);
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
		return handleRequest(RequestType.DELETE, null, null, null, user);
	}


	/**
	 * See {@link #handleRequest(RequestType, DataConfiguration, String, LoadDataRequest, UserEntity, Integer, Integer, RowSelector)}
	 */
	private ResponseEntity<Object> handleRequest(
			final RequestType requestType,
			@Nullable final DataConfiguration configuration,
			@Nullable final String stepName,
			@Nullable final LoadDataRequest loadDataRequest,
			final UserEntity requestUser) throws ApiException {
		return handleRequest(requestType, configuration, stepName, loadDataRequest, requestUser, null, null, null);
	}

	/**
	 * Handles a request of the given type.
	 * For each RequestType, different attributes must not be null:
	 * <ul>
	 *     <li>{@link RequestType#ESTIMATE}: </li>
	 *     <li>{@link RequestType#DELETE}: requestUser</li>
	 *     <li>{@link RequestType#INFO}: requestUser, stepName</li>
	 *     <li>{@link RequestType#LOAD_CONFIG}: stepName, requestUser</li>
	 *     <li>{@link RequestType#LOAD_DATA}: loadDataRequest, requestUser, stepName</li>
	 *     <li>{@link RequestType#LOAD_DATA_SET}: loadDataRequest, requestUser, stepName</li>
	 *     <li>{@link RequestType#LOAD_TRANSFORMATION_RESULT}: requestUser, stepName</li>
	 *     <li>{@link RequestType#LOAD_TRANSFORMATION_RESULT_PAGE}: page, perPage, requestUser, rowSelector, stepName</li>
	 *     <li>{@link RequestType#STORE_CONFIG}: configuration, requestUser</li>
	 *     <li>{@link RequestType#STORE_DATE_SET}: configuration, user</li>
	 * </ul>
	 *
	 * @param requestType       Type of the request.
	 * @param configuration     Configuration describing the source data.
	 * @param loadDataRequest   Settings for the data set export.
	 * @param stepName          The name of the step.
	 * @param page              The page number to be exported.
	 * @param perPage           The number of entries per page to be exported.
	 * @param rowSelector       Selector for wich rows should be exported.
	 * @param requestUser       User of the request.
	 * @return Response entity containing the response based on the request type or an error description.
	 */
	private ResponseEntity<Object> handleRequest(
			final RequestType requestType,
			@Nullable final DataConfiguration configuration,
			@Nullable final String stepName,
			@Nullable final LoadDataRequest loadDataRequest,
			final UserEntity requestUser,
			final Integer page,
			final Integer perPage,
			final RowSelector rowSelector
	) throws ApiException {
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity projectEntity =  projectService.getProject(user);

		final List<String> columnNames = loadDataRequest != null && !loadDataRequest.getColumns().isBlank()
		                                 ? List.of(loadDataRequest.getColumns().split(","))
		                                 : new ArrayList<>();

		final Object result;
		switch (requestType) {
			case CONFIRM_DATE_SET -> {
				if (projectEntity.getDataSets().containsKey(Step.VALIDATION) &&
				    !projectEntity.getDataSets().get(Step.VALIDATION).isStoredData()) {
					throw new BadDataSetIdException(BadDataSetIdException.NO_DATA_SET, "The data has not been stored!");
				}
				projectEntity.getDataSets().get(Step.VALIDATION).setConfirmedData(true);
				statusService.updateCurrentStep(projectEntity, Step.ANONYMIZATION);
				result = null;
			}
			case ESTIMATE -> {
				if (projectEntity.getFile() == null) {
					throw new BadStateException(BadStateException.NO_DATASET_FILE, "Estimating the data configuration requires the file for the dataset to be selected!");
				}

				final DataProcessor dataProcessor = dataProcessorService.getDataProcessor(
						projectEntity.getFile().getFileConfiguration().getFileType());
				final InputStream inputStream = new ByteArrayInputStream(projectEntity.getFile().getFile());
				result = dataProcessor.estimateDataConfiguration(inputStream,
				                                                 projectEntity.getFile().getFileConfiguration(),
				                                                 DatatypeEstimationAlgorithm.MOST_ESTIMATED);

				try {
					databaseService.storeDataConfiguration((DataConfiguration) result, projectEntity, Step.VALIDATION);
				} catch (final BadDataConfigurationException e) {
					throw new InternalInvalidResultException(InternalInvalidResultException.INVALID_ESTIMATION,
					                                         "Estimation created an invalid configuration!", e);
				}
			}
			case DELETE -> {
				databaseService.delete(projectEntity);
				statusService.updateCurrentStep(projectEntity, Step.UPLOAD);
				result = null;
			}
			case INFO -> {
				final Step step = Step.getStepOrThrow(stepName);
				result = databaseService.getInfo(projectEntity, step);
			}
			case LOAD_CONFIG -> {
				result = databaseService.exportDataConfiguration(projectEntity, Step.VALIDATION);
			}
			case LOAD_DATA -> {
				final Step step = Step.getStepOrThrow(stepName);
				final DataSet dataSet = databaseService.exportDataSet(projectEntity, columnNames, step);
				result = dataSetService.encodeDataRows(dataSet, projectEntity.getDataSets().get(step)
				                                                             .getDataTransformationErrors(),
				                                       loadDataRequest);
			}
			case LOAD_DATA_SET -> {
				final Step step = Step.getStepOrThrow(stepName);
				result = databaseService.exportDataSet(projectEntity, columnNames, step);
			}
			case LOAD_TRANSFORMATION_RESULT -> {
				final Step step = Step.getStepOrThrow(stepName);
				result = databaseService.exportTransformationResult(projectEntity, step);
			}
			case LOAD_TRANSFORMATION_RESULT_PAGE -> {
				final Step step = Step.getStepOrThrow(stepName);
				if (rowSelector != RowSelector.ALL) {
					result = databaseService.exportTransformationResultPage(projectEntity, step, columnNames, page, perPage,
					                                                        rowSelector, loadDataRequest);
				} else {
					result = databaseService.exportTransformationResultPage(projectEntity, step, columnNames, page,
					                                                        perPage, loadDataRequest);
				}
			}
			case STORE_CONFIG -> {
				databaseService.storeDataConfiguration(configuration, projectEntity, Step.VALIDATION);
				result = null;
			}
			case STORE_DATE_SET -> {
				if (projectEntity.getFile() == null) {
					throw new BadStateException(BadStateException.NO_DATASET_FILE, "Storing the dataset requires the file for the dataset to be selected!");
				}

				// Store configuration
				databaseService.storeDataConfiguration(configuration, projectEntity, Step.VALIDATION);

				// Store data set
				final DataProcessor dataProcessor = dataProcessorService.getDataProcessor(
						projectEntity.getFile().getFileConfiguration().getFileType());
				final InputStream inputStream = new ByteArrayInputStream(projectEntity.getFile().getFile());

				final TransformationResult transformationResult = dataProcessor.read(inputStream,
				                                                                     projectEntity.getFile()
				                                                                                  .getFileConfiguration(),
				                                                                     configuration);
				result = databaseService.storeTransformationResult(transformationResult, projectEntity, Step.VALIDATION);

				statusService.updateCurrentStep(projectEntity, Step.VALIDATION);
			}
			default -> {
				throw new InternalMissingHandlingException(InternalMissingHandlingException.REQUEST_TYPE,
				                                           "Missing handling for request type '" + requestType.name() +
				                                           "'");
			}
		}
		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	private enum RequestType {
		CONFIRM_DATE_SET,
		ESTIMATE,
		DELETE,
		INFO,
		LOAD_CONFIG,
		LOAD_DATA,
		LOAD_DATA_SET,
		LOAD_TRANSFORMATION_RESULT,
		LOAD_TRANSFORMATION_RESULT_PAGE,
		STORE_CONFIG,
		STORE_DATE_SET,
	}

}
