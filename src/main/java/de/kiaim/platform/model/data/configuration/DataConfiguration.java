package de.kiaim.platform.model.data.configuration;

import de.kiaim.platform.model.data.DataType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * The complete data configuration that
 * stores global information as well as
 * all the ColumnConfiguration objects
 */
@Schema(description = "Metadata describing the format of a data set.")
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class DataConfiguration {
	public DataConfiguration() {
		this.configurations = new ArrayList<>();
	}

	/**
	 * Datatype and their order of all objects inside a DataRow.
	 */
	@Schema(description = "Order of the data types.",
	        example = "[\"BOOLEAN\",\"DATE\",\"DATE_TIME\",\"DECIMAL\",\"INTEGER\",\"STRING\"]")
	List<DataType> dataTypes;

	/**
	 * A list of configuration objects for every column
	 */
	@Schema(description = "One configuration for each column in the data set.",
	        example = "[{\"index\":0,\"name\":\"deceased\",\"type\":\"BOOLEAN\",\"configurations\":[]},{\"index\":1,\"name\":\"column1_date\",\"type\":\"DATE\",\"configurations\":[{\"name\":\"DateFormatConfiguration\",\"dateFormatter\":\"yyyy-MM-dd\"}]},{\"index\":2,\"name\":\"column2_date_time\",\"type\":\"DATE_TIME\",\"configurations\":[{\"name\":\"DateTimeFormatConfiguration\",\"dateTimeFormatter\":\"yyyy-MM-dd'T'HH:mm:ss.SSSSSS\"}]},{\"index\":3,\"name\":\"column3_decimal\",\"type\":\"DECIMAL\",\"configurations\":[]},{\"index\":4,\"name\":\"column4_integer\",\"type\":\"INTEGER\",\"configurations\":[]},{\"index\":5,\"name\":\"column5_string\",\"type\":\"STRING\",\"configurations\":[{\"name\":\"StringPatternConfiguration\",\"pattern\":\".*\"}]}]")
	List<ColumnConfiguration> configurations;

	/**
	 * Adds a ColumnConfiguration to the list
	 * of configurations
	 * @param columnConfiguration A ColumnConfiguration object
	 */
	public void addColumnConfiguration(ColumnConfiguration columnConfiguration) {
		this.configurations.add(columnConfiguration);
	}

	public List<DataType> getDataTypes() {
		if (this.dataTypes != null && !this.dataTypes.isEmpty()) {
			return this.dataTypes;
		} else {
			List<DataType> result = new ArrayList<>();

			for (ColumnConfiguration columnConfiguration : this.configurations) {
				result.add(columnConfiguration.getType());
			}

			return result;
		}
	}

	public List<String> getColumnNames() {
		return configurations.stream().map(ColumnConfiguration::getName).toList();
	}
}
