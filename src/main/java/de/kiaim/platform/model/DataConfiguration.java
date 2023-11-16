package de.kiaim.platform.model;

import de.kiaim.platform.model.data.DataType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DataConfiguration {

	/**
	 * Datatype and their order of all objects inside a DataRow.
	 */
	final List<DataType> dataTypes;
}
