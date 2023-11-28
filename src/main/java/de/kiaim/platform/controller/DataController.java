package de.kiaim.platform.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.model.data.exception.BadConfigurationException;
import de.kiaim.platform.model.data.exception.BadFileException;
import de.kiaim.platform.model.data.exception.DataSetPersistanceException;
import de.kiaim.platform.processor.CsvProcessor;
import de.kiaim.platform.processor.DataProcessor;
import de.kiaim.platform.service.DatabaseService;
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
// TODO Error codes?
@RestController
@RequestMapping("/api/data")
public class DataController {

	// TODO Find Processor dynamically
	private final CsvProcessor csvProcessor;
	private final DatabaseService databaseService;
	private final ObjectMapper objectMapper;

	@Autowired
	public DataController(final CsvProcessor csvProcessor, DatabaseService databaseService, ObjectMapper objectMapper) {
		this.csvProcessor = csvProcessor;
		this.databaseService = databaseService;
		this.objectMapper = objectMapper;
	}

	@PostMapping(value = "/datatypes", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> estimateDatatpes(@RequestBody MultipartFile file) {
		return handleRequest(RequestType.DATA_TYPES, file, null, null);
	}

	@PostMapping(value = "/validation", produces = MediaType.APPLICATION_JSON_VALUE,
	             consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Object> readAndValidateData(@RequestPart MultipartFile file,
	                                                  @RequestParam String configuration) {
		return handleRequest(RequestType.VALIDATE, file, configuration, null);
	}

	@PostMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> storeData(@RequestPart MultipartFile file,
	                                        @RequestParam String configuration) {
		return handleRequest(RequestType.STORE, file, configuration, null);
	}

	// TODO what to load
	public void loadData() {
	}

	@DeleteMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> deleteData(@RequestParam Long dataSetId) {
		return handleRequest(RequestType.DELETE, null, null , dataSetId);
	}

	/**
	 * For each RequestTypes requires different attributes to be not null:
	 * <ul>
	 *     <li>{@link RequestType#DATA_TYPES}: file</li>
	 *     <li>{@link RequestType#DELETE}: dataSetId</li>
	 *     <li>{@link RequestType#STORE}: file, configuration</li>
	 *     <li>{@link RequestType#VALIDATE}: file, configuration</li>
	 * </ul>
	 *
	 *
	 */
	private ResponseEntity<Object> handleRequest(
			final RequestType requestType,
			@Nullable final MultipartFile file,
			@Nullable final String configuration,
			@Nullable final Long dataSetId
	) {
		try {
			return doHandleRequest(requestType, file, configuration, dataSetId);
		} catch (BadConfigurationException | BadFileException e) {
			return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
		} catch (DataSetPersistanceException e) {
			return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	private ResponseEntity<Object> doHandleRequest(
			final RequestType requestType,
			final MultipartFile file,
			final String configuration,
			final Long dataSetId
	) throws BadFileException, DataSetPersistanceException, BadConfigurationException {
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
			case STORE -> {
				final DataProcessor dataProcessor = getDataProcessor(file);
				final InputStream inputStream = getInputStream(file);
				final DataConfiguration dataConfiguration = getDataConfiguration(configuration);
				final TransformationResult transformationResult = dataProcessor.read(inputStream, dataConfiguration);
				result = databaseService.store(transformationResult.getDataSet());
			}
			case VALIDATE -> {
				final DataProcessor dataProcessor = getDataProcessor(file);
				final InputStream inputStream = getInputStream(file);
				final DataConfiguration dataConfiguration = getDataConfiguration(configuration);
				result = dataProcessor.read(inputStream, dataConfiguration);
			}
			default -> {
				return new ResponseEntity<>("Missing handling for request type '" + requestType.name() + "'",
				                            HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}
		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	private DataConfiguration getDataConfiguration(final String configuration) throws BadConfigurationException {
		try {
			return objectMapper.readValue(configuration, DataConfiguration.class);
		} catch (JsonProcessingException e) {
			throw new BadConfigurationException("Invalid format of the configuration", e);
		}
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
		STORE,
		VALIDATE;
	}

}
