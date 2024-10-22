package de.kiaim.model.data;

import de.kiaim.model.configuration.data.ColumnConfiguration;
import de.kiaim.model.configuration.data.Configuration;
import de.kiaim.model.exception.DataBuildingException;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.ArrayList;
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

    /**
     * Builds the Data object containing a null value.
     * @return the new Data object.
     */
     Data buildNull();

     default ImmutablePair<Boolean, List<Configuration>> estimateColumnConfiguration(String value) {
         boolean success;
	     try {
		     this.setValue(value, new ArrayList<>()).build();
			 success = true;
	     } catch (DataBuildingException e) {
			 success = false;
	     }

         return ImmutablePair.of(success,new ArrayList<>());
     }
}
