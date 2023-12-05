package de.kiaim.platform.controller;

import de.kiaim.platform.exception.ApiException;
import de.kiaim.platform.model.DataSet;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.data.DataRow;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.exception.BadDataSetIdException;
import de.kiaim.platform.exception.BadFileException;
import de.kiaim.platform.exception.InternalDataSetPersistenceException;
import de.kiaim.platform.model.dto.ErrorResponse;
import de.kiaim.platform.processor.CsvProcessor;
import de.kiaim.platform.processor.DataProcessor;
import de.kiaim.platform.service.DatabaseService;
import de.kiaim.platform.service.ResponseService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

// TODO Support different languages
@RestController
@RequestMapping("/api/data")
@Tag(name = "/api/data", description = "API for managing data sets. Supports CSV files.")
public class DataController {

	// TODO Find Processor dynamically
	private final CsvProcessor csvProcessor;
	private final DatabaseService databaseService;
	private final ResponseService responseService;

	@Autowired
	public DataController(final CsvProcessor csvProcessor, DatabaseService databaseService,
	                      ResponseService responseService) {
		this.csvProcessor = csvProcessor;
		this.databaseService = databaseService;
		this.responseService = responseService;
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
	public ResponseEntity<Object> estimateDatatpes(
			@Parameter(description = "File containing the data.",
			           content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
			@RequestPart(value = "file") MultipartFile file
	) {
		return handleRequest(RequestType.DATA_TYPES, file, null, null);
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
			@Parameter(description = "File containing the data.",
			           content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
			@RequestPart(value = "file") MultipartFile file,
			@Parameter(description = "Metadata describing the format of the data.",
			           content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                              schema = @Schema(implementation = DataConfiguration.class)))
			@RequestParam(value = "configuration") DataConfiguration configuration
	) {
		return handleRequest(RequestType.VALIDATE, file, configuration, null);
	}

	@Operation(summary = "Stores the given data into the internal database for further processing.",
	           description = "Stores the given data into the internal database for further processing.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully stored the data. Returns the id of the data set.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = Long.class))}),
			@ApiResponse(responseCode = "400",
			             description = "The file is not supported or could not be read. The configuration is not valid.",
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
			@Parameter(description = "File containing the data to be stored.",
			           content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
			@RequestPart(value = "file") MultipartFile file,
			@Parameter(description = "Metadata describing the format of the data.",
			           content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                              schema = @Schema(implementation = DataConfiguration.class)))
			@RequestParam(value = "configuration") DataConfiguration configuration
	) {
		return handleRequest(RequestType.STORE, file, configuration, null);
	}

	@Operation(summary = "Returns the configuration of the data set with the given ID.",
	           description = "Returns the configuration of the data set with the given ID.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully found the configuration. Returns the configuration of the data set.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = DataConfiguration.class))}),
			@ApiResponse(responseCode = "400",
			             description = "The data set ID is malformed. No data set with the given ID exists.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping(value = "/configuration",
	            consumes = MediaType.APPLICATION_JSON_VALUE,
	            produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> loadConfig(
			@Parameter(description = "ID of the data set.",
			           content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                              schema = @Schema(implementation = Long.class)))
			@RequestParam("dataSetId") Long dataSetId
	) {
		return handleRequest(RequestType.LOAD_CONFIG, null, null, dataSetId);
	}

	@Operation(summary = "Returns the data of the data set with the given ID.",
	           description = "Returns the data of the data set with the given ID.")
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
			             description = "The data set ID is malformed. No data set with the given ID exists.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping(value = "/data",
	            consumes = MediaType.APPLICATION_JSON_VALUE,
	            produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> loadData(
			@Parameter(description = "ID of the data set.",
			           content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                              schema = @Schema(implementation = Long.class)))
			@RequestParam("dataSetId") Long dataSetId
	) {
		return handleRequest(RequestType.LOAD_DATA, null, null, dataSetId);
	}

	@Operation(summary = "Returns the data set with the given ID.",
	           description = "Returns the data set with the given ID.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully found the data set. Returns the data set.",
			             content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                 schema = @Schema(implementation = DataSet.class))}),
			@ApiResponse(responseCode = "400",
			             description = "The data set ID is malformed. No data set with the given ID exists.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping(value = "",
	            consumes = MediaType.APPLICATION_JSON_VALUE,
	            produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> loadDataSet(
			@Parameter(description = "ID of the data set to be returned.",
			           content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                              schema = @Schema(implementation = Long.class)))
			@RequestParam("dataSetId") Long dataSetId
	) {
		return handleRequest(RequestType.LOAD_DATA_SET, null, null, dataSetId);
	}

	@Operation(summary = "Deletes the data set with the given ID from the internal data base.",
	           description = "Deletes the data set with the given ID from the internal data base.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200",
			             description = "Successfully deleted the data set.",
			             content = @Content),
			@ApiResponse(responseCode = "400",
			             description = "The data set ID is malformed. No data set with the given ID exists.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500",
			             description = "An internal error occurred.",
			             content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                                schema = @Schema(implementation = ErrorResponse.class)))
	})
	@DeleteMapping(value = "",
	               consumes = MediaType.APPLICATION_JSON_VALUE,
	               produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> deleteData(
			@Parameter(description = "ID of the data set to be deleted.",
			           content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			                              schema = @Schema(implementation = Long.class)))
			@RequestParam("dataSetId") Long dataSetId
	) {
		return handleRequest(RequestType.DELETE, null, null, dataSetId);
	}

	/**
	 * Handles a request of the given type.
	 * For each RequestType, different attributes must not be null:
	 * <ul>
	 *     <li>{@link RequestType#DATA_TYPES}: file</li>
	 *     <li>{@link RequestType#DELETE}: dataSetId</li>
	 *     <li>{@link RequestType#LOAD_CONFIG}: dataSetId</li>
	 *     <li>{@link RequestType#LOAD_DATA}: dataSetId</li>
	 *     <li>{@link RequestType#LOAD_DATA_SET}: dataSetId</li>
	 *     <li>{@link RequestType#STORE}: file, configuration</li>
	 *     <li>{@link RequestType#VALIDATE}: file, configuration</li>
	 * </ul>
	 *
	 * @param requestType   Type of the request.
	 * @param file          File containing the source data.
	 * @param configuration Configuration describing the source data.
	 * @param dataSetId     ID of the data set.
	 * @return Response entity containing the response based on the request type or an error description.
	 */
	private ResponseEntity<Object> handleRequest(
			final RequestType requestType,
			@Nullable final MultipartFile file,
			@Nullable final DataConfiguration configuration,
			@Nullable final Long dataSetId
	) {
		try {
			return doHandleRequest(requestType, file, configuration, dataSetId);
		} catch (ApiException e) {
			return responseService.prepareErrorResponseEntity(e);
		}
	}

	private ResponseEntity<Object> doHandleRequest(
			final RequestType requestType,
			final MultipartFile file,
			final DataConfiguration configuration,
			final Long dataSetId
	) throws BadDataSetIdException, BadFileException, InternalDataSetPersistenceException {
		final Object result;
		switch (requestType) {
			case DATA_TYPES -> {
				final DataProcessor dataProcessor = getDataProcessor(file);
				final InputStream inputStream = getInputStream(file);
				result = dataProcessor.estimateDatatypes(inputStream);
			}
			case DELETE -> {
				databaseService.delete(dataSetId);
				result = null;
			}
			case LOAD_CONFIG -> {
				result = databaseService.exportDataConfiguration(dataSetId);
			}
			case LOAD_DATA -> {
				final DataSet dataSet = databaseService.exportDataSet(dataSetId);
				result = dataSet.getData();
			}
			case LOAD_DATA_SET -> {
				result = databaseService.exportDataSet(dataSetId);
			}
			case STORE -> {
				final DataProcessor dataProcessor = getDataProcessor(file);
				final InputStream inputStream = getInputStream(file);
				final TransformationResult transformationResult = dataProcessor.read(inputStream, configuration);
				result = databaseService.store(transformationResult.getDataSet());
			}
			case VALIDATE -> {
				final DataProcessor dataProcessor = getDataProcessor(file);
				final InputStream inputStream = getInputStream(file);
				result = dataProcessor.read(inputStream, configuration);
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
		STORE,
		VALIDATE;
	}

}
