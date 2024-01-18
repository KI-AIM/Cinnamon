package de.kiaim.platform.model.data.configuration;

import de.kiaim.platform.model.data.DataType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Every column stores its own configuration
 * to allow for dynamically constructed data
 * configurations
 */
@Schema(description = "Configuration of a single column in the data set.")
@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
public class ColumnConfiguration {
    public ColumnConfiguration() {
        this.configurations = new ArrayList<>();
    }

    /**
     * The index of the column
     */
    @Schema(description = "Index of the column in the data set.", example = "1")
    int index;

    /**
     * The name of the column
     */
    @Schema(description = "Name of the column.", example = "dateOfBirth")
    String name;

    /**
     * The datatype of the column
     */
    @Schema(description = "Data type of the column.", example = "DATE")
    DataType type;

    /**
     * The scale of the column
     */
    @Schema(description = "Data scale of the column.", example = "INTERVAL")
    DataScale scale;

    /**
     * A list of configurations for the column
     */
    @Schema(description = "List of different configurations depending on the data type.",
            example = "[{\"name\": \"DateFormatConfiguration\", \"dataFormatter\": \"yyyy-MM-dd\"}]")
    List<Configuration> configurations;



    /**
     * Adds a new configuration to the column configuration
     * @param configuration to add
     */
    public void addConfiguration(Configuration configuration) {
        this.configurations.add(configuration);
    }
}
