package de.kiaim.model.helper;

import de.kiaim.model.configuration.data.ColumnConfiguration;
import de.kiaim.model.data.*;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.model.exception.ConfigurationFormatException;
import de.kiaim.model.exception.DataBuildingException;
import de.kiaim.model.exception.MissingValueException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Helper class for functions that can be used
 * when transforming string data to the internal
 * data-scheme
 */
@Component
public class DataTransformationHelper {

    /**
     * Method transforms the given String to the
     * correct data type depicted by the ColumnConfiguration
     * @param value the String value
     * @param configuration The ColumnConfiguration for the value
     * @return Instance of an Implementation of the Data Interface
     * @throws DataBuildingException if anything is faulty when creating the object
     */
    public Data transformData(String value, ColumnConfiguration configuration) throws DataBuildingException {
        if (isValueEmpty(value)) {
            throw new MissingValueException();
        }

        final DataBuilder dataBuilder = getDataBuilder(configuration.getType());
        if (dataBuilder == null) {
            return null;
        }

        return dataBuilder.setValue(value, configuration.getConfigurations()).build();
    }

    public Data transformNullValue(ColumnConfiguration configuration) {
        return switch (configuration.getType()) {
            case BOOLEAN -> new BooleanData(null);
            case DATE -> new DateData(null);
            case DATE_TIME -> new DateTimeData(null);
            case DECIMAL -> new DecimalData(null);
            case INTEGER -> new IntegerData(null);
            case STRING -> new StringData(null);
            case UNDEFINED -> null;
        };
    }

    public DataBuilder getDataBuilderOrThrow(DataType dataType) throws ConfigurationFormatException {
        final DataBuilder dataBuilder = getDataBuilder(dataType);
        if (dataBuilder == null) {
            throw new ConfigurationFormatException();
        }
        return dataBuilder;
    }

    @Nullable
    public DataBuilder getDataBuilder(DataType dataType) {
        return switch (dataType) {
            case BOOLEAN -> new BooleanData.BooleanDataBuilder();
            case DATE -> new DateData.DateDataBuilder();
            case DATE_TIME -> new DateTimeData.DateTimeDataBuilder();
            case DECIMAL -> new DecimalData.DecimalDataBuilder();
            case INTEGER -> new IntegerData.IntegerDataBuilder();
            case STRING -> new StringData.StringDataBuilder();
            case UNDEFINED -> null;
        };
    }

    /**
     * Checks whether a value is empty
     * @param value to be checked
     * @return true if value is empty; false otherwise
     */
    public boolean isValueEmpty(String value) {
        return value.isEmpty() ||
                value.equals("N/A") ||
                value.equals("NaN") ||
                value.equals("null");
    }

    /**
     * Inverse result of isValueEmpty.
     * Can be used to improve readability
     * @param value to be checked
     * @return true if value is not empty; false otherwise
     */
    public boolean isValueNotEmpty(String value) {
        return !isValueEmpty(value);
    }
}
