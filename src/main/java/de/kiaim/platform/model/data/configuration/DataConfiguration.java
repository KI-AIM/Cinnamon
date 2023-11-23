package de.kiaim.platform.model.data.configuration;

import de.kiaim.platform.model.data.DataType;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * The complete data configuration that
 * stores global information as well as
 * all the ColumnConfiguration objects
 */
@Getter
@Setter
public class DataConfiguration {
	public DataConfiguration() {
		this.configurations = new ArrayList<>();
	}

	/**
	 * Datatype and their order of all objects inside a DataRow.
	 */
	List<DataType> dataTypes;

	/**
	 * A list of configuration objects for every column
	 */
	List <ColumnConfiguration> configurations;

	/**
	 * Adds a ColumnConfiguration to the list
	 * of configurations
	 * @param columnConfiguration A ColumnConfiguration object
	 */
	public void addColumnConfiguration(ColumnConfiguration columnConfiguration) {
		this.configurations.add(columnConfiguration);
	}

}
