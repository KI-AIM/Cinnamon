package de.kiaim.model.data;

import de.kiaim.model.configuration.data.Configuration;
import de.kiaim.model.exception.DataBuildingException;

import java.util.List;

/**
 * Interface for all data builder classes
 */
public interface DataBuilder {

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

}
