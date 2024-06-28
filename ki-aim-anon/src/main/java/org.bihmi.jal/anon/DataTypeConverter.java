package org.bihmi.jal.anon;
import org.deidentifier.arx.DataType;

public class DataTypeConverter {
    public static DataType get(String datatype){
        return switch (datatype.toUpperCase()) {
            case "DATE" -> DataType.DATE;
            case "STRING" -> DataType.STRING;
            case "INTEGER" -> DataType.INTEGER;
            case "DECIMAL" -> DataType.DECIMAL;
            case "ORDERED_STRING" -> DataType.ORDERED_STRING;
            default -> throw new IllegalArgumentException("String " + datatype + " could not be mapped to any ARX datatype.");
        };
    }
}
