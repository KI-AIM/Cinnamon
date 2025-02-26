package de.kiaim.platform.service;

import de.kiaim.platform.exception.BadFileException;
import de.kiaim.platform.exception.InternalMissingHandlingException;
import de.kiaim.platform.model.file.FileType;
import de.kiaim.platform.processor.DataProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class DataProcessorService {

	private final List<DataProcessor> processors;

	@Autowired
	public DataProcessorService(final List<DataProcessor> processors) {
		this.processors = processors;
	}

	/**
	 * Returns the DataProcessor that can handle the given file type.
	 * @param fileType The file type of the file to process.
	 * @return The DataProcessor.
	 * @throws InternalMissingHandlingException If no data processor exists for the given file type.
	 */
	public DataProcessor getDataProcessor(final FileType fileType) throws InternalMissingHandlingException {
		for (final DataProcessor processor : processors) {
			if (processor.getSupportedDataType().equals(fileType)) {
				return processor;
			}
		}

		throw new InternalMissingHandlingException(InternalMissingHandlingException.FILE_TYPE , "Unsupported file type: '" + fileType.name() + "'");
	}

	/**
	 * Validates the given file.
	 * @param file File to be validated.
	 * @throws BadFileException If the file is not valid.
	 */
	public void validateFileOrThrow(@Nullable final MultipartFile file) throws BadFileException {
		if (file == null) {
			throw new BadFileException(BadFileException.MISSING_FILE, "Missing file");
		}

		final String fileName = file.getOriginalFilename();
		if (fileName == null || fileName.isBlank()) {
			throw new BadFileException(BadFileException.MISSING_FILE_NAME, "Missing filename");
		}

		final int fileExtensionBegin = file.getOriginalFilename().lastIndexOf('.');
		if (fileExtensionBegin == -1) {
			throw new BadFileException(BadFileException.MISSING_FILE_EXTENSION, "Missing file extension");
		}
	}

}
