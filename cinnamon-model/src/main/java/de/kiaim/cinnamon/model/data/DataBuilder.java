package de.kiaim.cinnamon.model.data;

import de.kiaim.cinnamon.model.configuration.data.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.Configuration;
import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.model.exception.DataBuildingException;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface for all data builder classes
 */
public interface DataBuilder {

	/**
	 * Returns the data type of the Data object build by this builder.
	 * @return The DataType.
	 */
	DataType getDataType();

    /**
     * Sets the value of the resulting Data Object
     * @param value The String value to be set
     * @param configuration The List of Configuration objects for the column
     * @return DataBuilder (this)
     * @throws DataBuildingException if value cannot be set and validated
     */
    DataBuilder setValue(String value, List<Configuration> configuration) throws DataBuildingException;

    /**
     * Builds the BooleanData Object.
     * Only to be called after setValue()
     * @return new BooleanData object
     */
     Data build();

    /**
     * Builds the Data object containing a null value.
     * @return the new Data object.
     */
     Data buildNull();

	/**
	 * Estimates the data type and configurations for the given value.
	 * @param value The raw value.
	 * @return A ColumnConfiguration containing the estimated values.
	 */
     default ColumnConfiguration estimateColumnConfiguration(final String value) {
	     final var columnConfiguration = new ColumnConfiguration();

	     try {
		     this.setValue(value, new ArrayList<>()).build();
		     columnConfiguration.setType(getDataType());
	     } catch (DataBuildingException ignored) {
	     }

		 return columnConfiguration;
     }
}
