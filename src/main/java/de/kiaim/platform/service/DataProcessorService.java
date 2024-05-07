package de.kiaim.platform.service;

import de.kiaim.platform.exception.BadFileException;
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
	 * Returns the DataProcessor that can handle the given file.
	 * @param file The file which the returned DataProcessor should handle.
	 * @return The DataProcessor.
	 * @throws BadFileException If nor DataProcessor can handle the given file or the file extension could not be read.
	 */
	public DataProcessor getDataProcessor(final MultipartFile file) throws BadFileException {
		final String fileExtension = extractFileExtension(file);

		for (final DataProcessor processor : processors) {
			if (processor.getSupportedDataType().getFileExtensions().contains(fileExtension)) {
				return processor;
			}
		}

		throw new BadFileException("Unsupported file type: '" + fileExtension + "'");
	}

	/**
	 * Extracts the file extension from the file name of the given file, e.g. ".csv".
	 * @param file File to extract the extension from.
	 * @return The file extension.
	 * @throws BadFileException If the file extension could not be read.
	 */
	private String extractFileExtension(@Nullable final MultipartFile file) throws BadFileException {
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

}
