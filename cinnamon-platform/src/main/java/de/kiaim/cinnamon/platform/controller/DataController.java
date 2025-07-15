package de.kiaim.cinnamon.platform.controller;

import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.data.DataRow;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.model.dto.ErrorResponse;
import de.kiaim.cinnamon.model.spring.CustomMediaType;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.dto.*;
import de.kiaim.cinnamon.platform.service.*;
import de.kiaim.cinnamon.platform.model.entity.DataSetEntity;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.cinnamon.platform.model.enumeration.HoldOutSelector;
import de.kiaim.cinnamon.platform.model.enumeration.RowSelector;
import de.kiaim.cinnamon.platform.model.TransformationResult;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.processor.DataProcessor;
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
import java.util.Map;

// TODO Support different languages
@RestController
@RequestMapping("/api/data")
@Tag(name = "/api/data", description = "API for managing data sets. " +
                                       "Supports CSV files. " +
                                       "Data Sets are associated with the user of the request.")
public class DataController {

	private final DatabaseService databaseService;
	private final DataProcessorService dataProcessorService;
	private final DataSetService dataSetService;
	private final ProjectService projectService;
	private final UserService userService;

	@Autowired
	public DataController(final DatabaseService databaseService, final DataProcessorService dataProcessorService,
	                      final DataSetService dataSetService, final ProjectService projectService,
	                      final UserService userService) {
		this.databaseService = databaseService;
		this.dataProcessorService = dataProcessorService;
		this.dataSetService = dataSetService;
		this.projectService = projectService;
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
		return ResponseEntity.ok(fileInformation);
	}

	@Operation(summary = "Estimates the data configuration of a given data set.",
	           description = "Estimates the data configuration of a given data set.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully estimated the data configuration. Returns the estimated data configuration.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = DataConfigurationEstimation.class)),
			                        @Content(mediaType = CustomMediaType.APPLICATION_YAML_VALUE,
			                                 schema = @Schema(implementation = DataConfigurationEstimation.class))}),
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
			                                 schema = @Schema(implementation = DataConfiguration.class)),
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
			@ParameterObject @Valid final DataSetSource dataSetSource,
			@AuthenticationPrincipal final UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.LOAD_CONFIG, null, dataSetSource, null, user);
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
	@GetMapping(value = "/info",
	            produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Object> info(
			@ParameterObject @Valid final DataSetSource dataSetSource,
			@AuthenticationPrincipal final UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.INFO, null, dataSetSource, null, user);
	}

	@Operation(summary = "Returns the data of the data set.",
	           description = "Returns the data of the data.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully found the data and returns the data.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 array = @ArraySchema(schema = @Schema(implementation = DataRow.class)),
			                                 examples = {@ExampleObject("[" + DataRow.DATA_ROW_EXAMPLE + "]")}),
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
			@ParameterObject @Valid final DataSetSource dataSetSource,
			@ParameterObject LoadDataRequest request,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.LOAD_DATA, null, dataSetSource, request, user);
	}

	@Operation(summary = "Creates a hold-out split.",
	           description = "Creates a hold-out split.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "The hold-out split has been generated! Returns nothing.",
			             content = @Content(schema = @Schema())),
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
	@PostMapping(value = "/hold-out",
	            produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Object> generateHoldOutSplit(
			@ParameterObject @Valid final HoldOutRequest request,
			@AuthenticationPrincipal UserEntity requestUser
	) throws ApiException {
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity projectEntity =  projectService.getProject(user);
		databaseService.createHoldOutSplit(projectEntity, request.getHoldOutPercentage());
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@Operation(summary = "Returns the data set.",
	           description = "Returns the data set.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully found the data set. Returns the data set.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = DataSet.class)),
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
			@ParameterObject @Valid final DataSetSource dataSetSource,
			@ParameterObject LoadDataRequest request,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.LOAD_DATA_SET, null, dataSetSource, request, user);
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
	@GetMapping(value = "/transformationResult",
	            produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public ResponseEntity<Object> loadTransformationResult(
			@ParameterObject @Valid final DataSetSource dataSetSource,
			@ParameterObject LoadDataRequest request,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		return handleRequest(RequestType.LOAD_TRANSFORMATION_RESULT, null, dataSetSource, request, user);
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
	@GetMapping(value = "/transformationResult/page",
	            produces = {MediaType.APPLICATION_JSON_VALUE, CustomMediaType.APPLICATION_YAML_VALUE})
	public TransformationResultPage loadTransformationResultPage(
			@ParameterObject @Valid final DataSetSource dataSetSource,
			@Parameter(description = "Page number starting at 1.")
			@RequestParam(required = true) final Integer page,
			@Parameter(description = "Number of items per page.")
			@RequestParam(required = true) final Integer perPage,
			@Parameter(description = "Selector for the rows to be included.")
			@RequestParam(required = false, defaultValue = "ALL") final RowSelector rowSelector,
			@ParameterObject @Valid final LoadDataRequest request,
			@AuthenticationPrincipal UserEntity user
	) throws ApiException {
		final UserEntity user2 = userService.getUserByEmail(user.getEmail());
		final ProjectEntity project = projectService.getProject(user2);

		dataSetService.getDataSetEntityOrThrow(project, dataSetSource);
		return databaseService.exportTransformationResultPage(
				dataSetService.getDataSetEntityOrThrow(project, dataSetSource), rowSelector,
				page, perPage, request);
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
	 * Handles a request of the given type.
	 * For each RequestType, different attributes must not be null:
	 * <ul>
	 *     <li>{@link RequestType#ESTIMATE}: </li>
	 *     <li>{@link RequestType#DELETE}: requestUser</li>
	 *     <li>{@link RequestType#INFO}: requestUser, stepName</li>
	 *     <li>{@link RequestType#LOAD_CONFIG}: requestUser, stepName</li>
	 *     <li>{@link RequestType#LOAD_DATA}: loadDataRequest, requestUser, stepName</li>
	 *     <li>{@link RequestType#LOAD_DATA_SET}: loadDataRequest, requestUser, stepName</li>
	 *     <li>{@link RequestType#LOAD_TRANSFORMATION_RESULT}: requestUser, stepName</li>
	 *     <li>{@link RequestType#STORE_CONFIG}: configuration, requestUser</li>
	 *     <li>{@link RequestType#STORE_DATE_SET}: configuration, user</li>
	 * </ul>
	 *
	 * @param requestType       Type of the request.
	 * @param configuration     Configuration describing the source data.
	 * @param loadDataRequest   Settings for the data set export.
	 * @param dataSetSource     Source of the data set.
	 * @param requestUser       User of the request.
	 * @return Response entity containing the response based on the request type or an error description.
	 */
	private ResponseEntity<Object> handleRequest(
			final RequestType requestType,
			@Nullable final DataConfiguration configuration,
			@Nullable final DataSetSource dataSetSource,
			@Nullable final LoadDataRequest loadDataRequest,
			final UserEntity requestUser
	) throws ApiException {
		final UserEntity user = userService.getUserByEmail(requestUser.getEmail());
		final ProjectEntity projectEntity =  projectService.getProject(user);

		final List<String> columnNames = loadDataRequest != null
		                                 ? loadDataRequest.getColumnNames()
		                                 : new ArrayList<>();

		final HoldOutSelector holdOutSelector = loadDataRequest != null ? loadDataRequest.getHoldOutSelector() : HoldOutSelector.ALL;

		final Object result;
		switch (requestType) {
			case CONFIRM_DATE_SET -> {
				databaseService.confirmDataSet(projectEntity);
				result = null;
			}
			case ESTIMATE -> {
				final var file = projectEntity.getOriginalData().getFile();
				if (file == null) {
					throw new BadStateException(BadStateException.NO_DATASET_FILE, "Estimating the data configuration requires the file for the dataset to be selected!");
				}

				final DataProcessor dataProcessor = dataProcessorService.getDataProcessor(
						file.getFileConfiguration().getFileType());
				final InputStream inputStream = new ByteArrayInputStream(file.getFile());
				DataConfigurationEstimation estimation = dataProcessor.estimateDataConfiguration(inputStream,
				                                                                                 file.getFileConfiguration(),
				                                                                                 DatatypeEstimationAlgorithm.MOST_ESTIMATED);
				result = estimation;

				try {
					databaseService.storeOriginalDataConfiguration(estimation.getDataConfiguration(), projectEntity);
				} catch (final BadDataConfigurationException e) {
					throw new InternalInvalidResultException(InternalInvalidResultException.INVALID_ESTIMATION,
					                                         "Estimation created an invalid configuration!", e);
				}
			}
			case DELETE -> {
				databaseService.delete(projectEntity);
				result = null;
			}
			case INFO -> {
				result = databaseService.getInfo(projectEntity, dataSetSource);
			}
			case LOAD_CONFIG -> {
				result = databaseService.exportDataConfiguration(projectEntity, dataSetSource);
			}
			case LOAD_DATA -> {
				final DataSetEntity dataSetEntity = dataSetService.getDataSetEntityOrThrow(projectEntity, dataSetSource);
				final DataSet dataSet = databaseService.exportDataSet(dataSetEntity, columnNames, holdOutSelector);
				final Map<Integer, Integer> columnIndexMapping = dataSetService.getColumnIndexMapping(dataSetEntity.getDataConfiguration(), columnNames);
				result = dataSetService.encodeDataRows(dataSet, dataSetEntity.getDataTransformationErrors(),
													   columnIndexMapping, loadDataRequest);
			}
			case LOAD_DATA_SET -> {
				result = databaseService.exportDataSet(projectEntity, columnNames, holdOutSelector, dataSetSource);
			}
			case LOAD_TRANSFORMATION_RESULT -> {
				result = databaseService.exportTransformationResult(projectEntity, holdOutSelector, dataSetSource);
			}
			case STORE_CONFIG -> {
				databaseService.storeOriginalDataConfiguration(configuration, projectEntity);
				result = null;
			}
			case STORE_DATE_SET -> {
				final var file = projectEntity.getOriginalData().getFile();
				if (file == null) {
					throw new BadStateException(BadStateException.NO_DATASET_FILE, "Storing the dataset requires the file for the dataset to be selected!");
				}

				// Store configuration
				databaseService.storeOriginalDataConfiguration(configuration, projectEntity);

				// Store data set
				final DataProcessor dataProcessor = dataProcessorService.getDataProcessor(
						file.getFileConfiguration().getFileType());
				final InputStream inputStream = new ByteArrayInputStream(file.getFile());

				final TransformationResult transformationResult = dataProcessor.read(inputStream,
				                                                                     file.getFileConfiguration(),
				                                                                     configuration);
				result = databaseService.storeOriginalTransformationResult(transformationResult, projectEntity);
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
		STORE_CONFIG,
		STORE_DATE_SET,
	}

}
