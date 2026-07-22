package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.model.enumeration.DataSourceType;
import de.kiaim.cinnamon.platform.exception.InternalMissingHandlingException;
import de.kiaim.cinnamon.platform.processor.source.DataSourceProcessor;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Service for managing data source processors.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class DataSourceProcessorService {

	private final Set<DataSourceProcessor> processors;

	public DataSourceProcessorService(Set<DataSourceProcessor> processors) {
		this.processors = processors;
	}

	/**
	 * Returns the processor for the given data source type.
	 *
	 * @param type The data source type.
	 * @return The processor.
	 * @throws InternalMissingHandlingException If no processor exists for the given data source type.
	 */
	public DataSourceProcessor getProcessor(final DataSourceType type) throws InternalMissingHandlingException {
		for (final DataSourceProcessor processor : processors) {
			if (processor.getSupportedDataSourceType().equals(type)) {
				return processor;
			}
		}

		throw new InternalMissingHandlingException(InternalMissingHandlingException.DATA_SOURCE_TYPE,
		                                           "No processor found for data source type: " + type);
	}
}
