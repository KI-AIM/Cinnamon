package de.kiaim.platform.converter;


import de.kiaim.platform.model.enumeration.DataSetSourceSelector;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Converts a string to a {@link DataSetSourceSelector} in incoming requests.
 * Is automatically applied without additional configuration.
 *
 * @author Daniel Preciado-Marquez
 */
@Component
public class StringToDataSetSourceSelectorConverter implements Converter<String, DataSetSourceSelector> {

	@Nullable @Override
	public DataSetSourceSelector convert(final String source) {
		return DataSetSourceSelector.valueOf(source.trim().toUpperCase());
	}
}
