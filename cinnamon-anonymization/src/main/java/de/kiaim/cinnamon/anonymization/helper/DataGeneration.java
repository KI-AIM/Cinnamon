package de.kiaim.cinnamon.anonymization.helper;

import de.kiaim.cinnamon.model.data.*;
import de.kiaim.cinnamon.model.enumeration.DataType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class DataGeneration {

    /**
     * Creates a Data object of the specified type with the provided value.
     *
     * @param type  The DataType of the data to be created.
     * @param value The value for the Data object, must be of a type compatible with the DataType.
     * @return A new instance of a subclass of Data corresponding to the specified DataType.
     * @throws IllegalArgumentException If the DataType is unsupported or if the value type is not compatible.
     */
    public static Data createDataByType(DataType type, Object value) {
        return switch (type) {
            case DATE -> new DateData((LocalDate) value);
            case BOOLEAN -> new BooleanData((Boolean) value);
            case DATE_TIME -> new DateTimeData((LocalDateTime) value);
            case DECIMAL -> new DecimalData((Float) value);
            case INTEGER -> new IntegerData((Integer) value);
            case STRING -> new StringData((String) value);
            default -> throw new IllegalArgumentException("Unsupported data type: " + type);
        };
    }

    /**
     * Creates a Data object based on the specified DataType, handling null values by returning
     * default values for each DataType.
     *
     * @param type  The DataType for which to create the Data object.
     * @param value The value for the Data object; if null, a default value appropriate for the DataType is used.
     * @return A Data object of the specified DataType, initialized with either the provided value or a default value.
     * @throws IllegalArgumentException If the DataType is unsupported or if the value, when non-null, is not compatible.
     */
    public static Data createDataByTypeAndValue(DataType type, Object value) {
        if (value == null) {
            // TODO : discuss default values
            return switch (type) {
                case DATE -> new DateData(null);
                case DATE_TIME -> new DateTimeData(null);
                case INTEGER -> new IntegerData(null);
                case DECIMAL -> new DecimalData(null);
                case BOOLEAN -> new BooleanData(null);
                case STRING -> new StringData(null);
                default -> throw new IllegalArgumentException("Unsupported data type: " + type);
            };
        } else {
            return switch (type) {
                case DATE -> new DateData((LocalDate) value);
                case INTEGER -> new IntegerData((Integer) value);
                case DECIMAL -> new DecimalData((Float) value);
                case BOOLEAN -> new BooleanData((Boolean) value);
                case STRING -> new StringData(value.toString());
                case DATE_TIME -> new DateTimeData((LocalDateTime) value);
                default -> throw new IllegalArgumentException("Unsupported data type: " + type);
            };
        }
    }
}
