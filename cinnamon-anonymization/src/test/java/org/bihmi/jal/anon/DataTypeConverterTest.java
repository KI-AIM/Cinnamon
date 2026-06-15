package org.bihmi.jal.anon;

import org.deidentifier.arx.DataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataTypeConverterTest {

    @Test
    void testDataTypeConverter() {
        assertEquals(DataType.STRING, DataTypeConverter.get("TEXT"));
    }
}
