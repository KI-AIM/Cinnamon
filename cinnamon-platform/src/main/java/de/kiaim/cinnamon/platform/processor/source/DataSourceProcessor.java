package de.kiaim.cinnamon.platform.processor.source;

import de.kiaim.cinnamon.model.configuration.data.DataSourceServerConfiguration;
import de.kiaim.cinnamon.model.configuration.data.file.FileType;
import de.kiaim.cinnamon.model.enumeration.DataSourceType;
import de.kiaim.cinnamon.platform.exception.InternalRequestException;
import org.springframework.data.util.Pair;
import org.springframework.web.multipart.MultipartFile;

/**
 * Marker interface for all data source processors
 *
 * @author Daniel Preciado-Marquez
 */
public interface DataSourceProcessor {

	/**
	 * Returns the data source type this processor can handle.
	 * @return The data source type.
	 */
	DataSourceType getSupportedDataSourceType();

	/**
	 * Retrieves the file from the data source.
	 *
	 * @param config The data source configuration.
	 * @return The file type and the file.
	 */
	Pair<FileType, MultipartFile> retrieveFile(DataSourceServerConfiguration config) throws InternalRequestException;
}
