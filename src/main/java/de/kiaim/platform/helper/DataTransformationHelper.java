package de.kiaim.platform.helper;

import de.kiaim.platform.model.data.configuration.*;
import de.kiaim.platform.model.data.*;

public class DataTransformationHelper {



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
