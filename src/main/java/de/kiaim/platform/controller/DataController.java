package de.kiaim.platform.controller;

import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.processor.CsvProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

// TODO Support different languages
// TODO Error codes?
@RestController
@RequestMapping("/api/data")
public class DataController {

	// TODO Find Processor dynamically
	private final CsvProcessor csvProcessor;

	@Autowired
	public DataController(final CsvProcessor csvProcessor) {
		this.csvProcessor = csvProcessor;
	}

	@PostMapping(value = "/datatypes", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> estimateDatatpes(@RequestBody MultipartFile file) {
		if (file == null) {
			return new ResponseEntity<>("Missing file", HttpStatus.BAD_REQUEST);
		}

		final String fileName = file.getOriginalFilename();
		if (fileName == null || fileName.isBlank()) {
			return new ResponseEntity<>("Missing filename", HttpStatus.BAD_REQUEST);
		}

		final int fileExtensionBegin = file.getOriginalFilename().lastIndexOf('.');
		if (fileExtensionBegin == -1) {
			return new ResponseEntity<>("Missing file extension", HttpStatus.BAD_REQUEST);
		}

		final String fileExtension = file.getOriginalFilename().substring(fileExtensionBegin);

		switch (fileExtension) {
			case ".csv":
				try {
					final DataConfiguration dataConfiguration =  csvProcessor.estimateDatatypes(file.getInputStream());
					return new ResponseEntity<>(dataConfiguration, HttpStatus.OK);
				} catch (IOException e) {
					return new ResponseEntity<>("Could not read file", HttpStatus.BAD_REQUEST);
				}
			default:
				return new ResponseEntity<>("Unsupported file type: '" + fileExtension + "'", HttpStatus.BAD_REQUEST);
		}
    }

	public void readAndValidateData() {

	}

	public void storeData() {

	}

	public void loadData() {

	}

	public void deleteData() {

	}

}
