package de.kiaim.platform.model.data.configuration;

import de.kiaim.platform.model.data.DataType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor
@Getter
public class ColumnConfiguration {

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
}
