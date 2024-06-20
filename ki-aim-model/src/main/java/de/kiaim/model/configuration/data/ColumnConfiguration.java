package de.kiaim.model.configuration.data;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.kiaim.model.enumeration.DataScale;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.model.serialization.ColumnConfigurationDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

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
@Setter
@EqualsAndHashCode
@ToString
@JsonDeserialize(using = ColumnConfigurationDeserializer.class)
public class ColumnConfiguration {
    public ColumnConfiguration() {
        this.configurations = new ArrayList<>();
    }

    /**
     * The index of the column
     */
    @Schema(description = "Index of the column in the data set.", example = "1")
    @NotNull(message = "The index must not be empty!")
    @Min(value = 0, message = "The index must not be negative!")
    Integer index;

    /**
     * The name of the column
     */
    @Schema(description = "Name of the column.", example = "dateOfBirth")
    @NotBlank(message = "The column name must not be empty!")
    @Pattern(regexp = "^\\S+$", message = "The column name must not contain space characters!")
    String name;

    /**
     * The datatype of the column
     */
    @Schema(description = "Data type of the column.", example = "DATE")
    @NotNull(message = "The data type must not be empty!")
    DataType type;

    /**
     * The scale of the column
     */
    @Schema(description = "Data scale of the column.", example = "INTERVAL")
    @NotNull(message = "The data scale must not be empty!")
    DataScale scale;

    /**
     * A list of configurations for the column
     */
    @Schema(description = "List of different configurations depending on the data type.",
            example = "[{\"name\": \"DateFormatConfiguration\", \"dataFormatter\": \"yyyy-MM-dd\"}]")
    @Valid
    List<Configuration> configurations;

    /**
     * Adds a new configuration to the column configuration
     * @param configuration to add
     */
    public void addConfiguration(Configuration configuration) {
        this.configurations.add(configuration);
    }
}
