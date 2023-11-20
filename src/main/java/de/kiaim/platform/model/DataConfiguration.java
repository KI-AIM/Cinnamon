package de.kiaim.platform.model;

import de.kiaim.platform.model.data.DataType;
import lombok.Getter;
import lombok.Setter;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Getter
@Setter
public class DataConfiguration {

	/**
	 * Datatype and their order of all objects inside a DataRow.
	 */
	List<DataType> dataTypes;

	/**
	 * The Formatter for all DateTime columns
	 */
	DateTimeFormatter dateTimeFormatter;

	/**
	 * The Formatter for all Date columns
	 */
	DateTimeFormatter dateFormatter;

}
