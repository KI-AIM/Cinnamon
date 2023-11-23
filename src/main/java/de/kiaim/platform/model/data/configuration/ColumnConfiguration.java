package de.kiaim.platform.model.data.configuration;

import de.kiaim.platform.model.data.DataType;
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
    int index;

    /**
     * The name of the column
     */
    String name;

    /**
     * The datatype of the column
     */
    DataType type;

    /**
     * A list of configurations for the column
     */
    List<Configuration> configurations;


    /**
     * Adds a new configuration to the column configuration
     * @param configuration to add
     */
    public void addConfiguration(Configuration configuration) {
        this.configurations.add(configuration);
    }
}
