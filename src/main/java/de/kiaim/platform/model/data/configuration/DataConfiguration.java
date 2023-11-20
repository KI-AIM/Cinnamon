package de.kiaim.platform.model.data.configuration;

import de.kiaim.platform.model.data.DataType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DataConfiguration {

	/**
	 * Datatype and their order of all objects inside a DataRow.
	 */
	List<DataType> dataTypes;

	/**
	 * A list of configuration objects for every column
	 */
	List <ColumnConfiguration> configurations;

	public void addColumnConfiguration(ColumnConfiguration columnConfiguration) {
		this.configurations.add(columnConfiguration);
	}

}
