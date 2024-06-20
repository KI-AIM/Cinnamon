package de.kiaim.model.configuration.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.model.validation.UniqueColumnNamesConstraint;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.Nullable;

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
	 * A list of configuration objects for every column
	 */
	@Schema(description = "One configuration for each column in the data set.",
			example = "[{\"index\":0,\"name\":\"deceased\",\"type\":\"BOOLEAN\",\"configurations\":[]},{\"index\":1,\"name\":\"column1_date\",\"type\":\"DATE\",\"configurations\":[{\"name\":\"DateFormatConfiguration\",\"dateFormatter\":\"yyyy-MM-dd\"},{\"name\":\"RangeConfiguration\",\"minValue\":\"1970-01-01\",\"maxValue\":\"2030-01-01\"}]},{\"index\":2,\"name\":\"column2_date_time\",\"type\":\"DATE_TIME\",\"configurations\":[{\"name\":\"DateTimeFormatConfiguration\",\"dateTimeFormatter\":\"yyyy-MM-dd'T'HH:mm:ss.SSSSSS\"},{\"name\":\"RangeConfiguration\",\"minValue\":\"1970-01-01T00:01:00\",\"maxValue\":\"2030-01-01T23:59:00\"}]},{\"index\":3,\"name\":\"column3_decimal\",\"type\":\"DECIMAL\",\"configurations\":[]},{\"index\":4,\"name\":\"column4_integer\",\"type\":\"INTEGER\",\"configurations\":[{\"name\":\"RangeConfiguration\",\"minValue\":0,\"maxValue\":100}]},{\"index\":5,\"name\":\"column5_string\",\"type\":\"STRING\",\"configurations\":[{\"name\":\"StringPatternConfiguration\",\"pattern\":\".*\"}]}]")
	@NotNull(message = "The column configurations must be present!")
	@UniqueColumnNamesConstraint
	@Valid
	List<ColumnConfiguration> configurations;

	/**
	 * Adds a ColumnConfiguration to the list
	 * of configurations
	 * @param columnConfiguration A ColumnConfiguration object
	 */
	public void addColumnConfiguration(ColumnConfiguration columnConfiguration) {
		this.configurations.add(columnConfiguration);
	}

	@JsonIgnore
	public List<DataType> getDataTypes() {
		List<DataType> result = new ArrayList<>();

		for (ColumnConfiguration columnConfiguration : this.configurations) {
			result.add(columnConfiguration.getType());
		}

		return result;
	}

	@JsonIgnore
	public List<String> getColumnNames() {
		return configurations.stream().map(ColumnConfiguration::getName).toList();
	}

	/**
	 * Returns the column configuration for the column with the given name.
	 * Returns null if no configuration with the given name exists.
	 * @param columnName Name of the column.
	 * @return ColumnConfiguration for the given column name or null.
	 */
	@JsonIgnore
	@Nullable
	public ColumnConfiguration getColumnConfigurationByColumnName(final String columnName) {
		for (final ColumnConfiguration columnConfiguration : configurations) {
			if (columnConfiguration.getName().equals(columnName)) {
				return columnConfiguration;
			}
		}

		return null;
	}
}
