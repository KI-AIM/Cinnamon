package de.kiaim.platform.helper;

import de.kiaim.platform.model.data.configuration.*;
import de.kiaim.platform.model.data.*;

/**
 * Helper class for functions that can be used
 * when transforming string data to the internal
 * data-scheme
 */
public class DataTransformationHelper {

    /**
     * Method transforms the given String to the
     * correct data type depicted by the ColumnConfiguration
     * @param value the String value
     * @param configuration The ColumnConfiguration for the value
     * @return Instance of an Implementation of the Data Interface
     * @throws Exception if anything is faulty when creating the object
     */
    public Data transformData(String value, ColumnConfiguration configuration) throws Exception {
        return switch (configuration.getType()) {
            case BOOLEAN -> new BooleanData.BooleanDataBuilder().setValue(value, configuration).build();
            case DATE -> new DateData.DateDataBuilder().setValue(value, configuration).build();
            case DATE_TIME -> new DateTimeData.DateTimeDataBuilder().setValue(value, configuration).build();
            case DECIMAL -> new DecimalData.DecimalDataBuilder().setValue(value, configuration).build();
            case INTEGER -> new IntegerData.IntegerDataBuilder().setValue(value, configuration).build();
            case STRING -> new StringData.StringDataBuilder().setValue(value, configuration).build();
        };
    }
}
