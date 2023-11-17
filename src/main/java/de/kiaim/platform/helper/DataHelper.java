package de.kiaim.platform.helper;

import de.kiaim.platform.model.data.*;

public class DataHelper {

    public Data transformData(String value, DataType type) throws Exception {
        return switch (type) {
            case BOOLEAN -> new BooleanData.BooleanDataBuilder().setValue(value).build();
            case DATE -> new DateData.DateDataBuilder().setValue(value).build();
            case DATE_TIME -> new DateTimeData.DateTimeDataBuilder().setValue(value).build();
            case DECIMAL -> new DecimalData.DecimalDataBuilder().setValue(value).build();
            case INTEGER -> new IntegerData.IntegerDataBuilder().setValue(value).build();
            case STRING -> new StringData.StringDataBuilder().setValue(value).build();
        };
    }
}
