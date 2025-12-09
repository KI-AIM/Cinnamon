package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.platform.exception.BadFileException;
import de.kiaim.cinnamon.platform.exception.InternalMissingHandlingException;
import de.kiaim.cinnamon.platform.model.file.FileType;
import de.kiaim.cinnamon.platform.processor.DataProcessor;
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
	 * Gets the file type of the given multipart file by analyzing the file extension.
	 *
	 * @param file The file.
	 * @return The file type of the file.
	 * @throws BadFileException If the file name is not valid.
	 */
	public FileType getFileType(final MultipartFile file) throws BadFileException {
		validateFileOrThrow(file);
		final String fileName = file.getOriginalFilename();
		final String fileExtension = getFileExtension(fileName);
		return getFileType(fileExtension);
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
	 * Extracts the part before the file extension from the file name.
	 *
	 * @param fileName The entire file name with extension.
	 * @return The file name without extension.
	 */
	public String getFileNameWithoutExtension(final String fileName) {
		return fileName.substring(0, fileName.lastIndexOf('.'));
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

	/**
	 * Returns the file extension of the given file name.
	 *
	 * @param fileName The file name.
	 * @return The file extension.
	 */
	private String getFileExtension(final String fileName) {
		return fileName.substring(fileName.lastIndexOf('.'));
	}

	/**
	 * Returns the file type for the given file extension.
	 *
	 * @param fileExtension The file extension.
	 * @return The file type.
	 * @throws BadFileException If the file extension is not supported.
	 */
	private FileType getFileType(final String fileExtension) throws BadFileException {
		for (final FileType fileType : FileType.values()) {
			if (fileType.getFileExtensions().contains(fileExtension)) {
				return fileType;
			}
		}
		throw new BadFileException(BadFileException.UNSUPPORTED,
		                           "The file extension '" + fileExtension + "' is not supported!");
	}

}
