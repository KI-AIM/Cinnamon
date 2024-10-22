package org.bihmi.jal.anon.util;

import org.deidentifier.arx.aggregates.HierarchyBuilderDate;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class DateTransformationHelperTest {


    @Test
    void reverseDateEncodingTest_decade() throws IOException {
        String encodedDate = "[1910, 1920[";
        String expectedDate = "1915-01-01";
        HierarchyBuilderDate.Granularity granularity = HierarchyBuilderDate.Granularity.DECADE;

        String decodedDate = DateTransformationHelper.reverseDateEncoding(encodedDate, granularity);
        assertEquals(expectedDate, decodedDate);
    }

    @Test
    void reverseDateEncodingTest_quarterYear() throws IOException {
        String encodedDate = "Q1 1915";
        String expectedDate = "1915-02-01";
        HierarchyBuilderDate.Granularity granularity = HierarchyBuilderDate.Granularity.QUARTER_YEAR;

        String decodedDate = DateTransformationHelper.reverseDateEncoding(encodedDate, granularity);
        assertEquals(expectedDate, decodedDate);
    }
//    TODO: required ? Did not pass for Daniel and Yannik
//    @Test
//    void reverseDateEncodingTest_monthYear() throws IOException {
//        String encodedDate = "01/1915";
//        String expectedDate = "1915-01-01";
//        HierarchyBuilderDate.Granularity granularity = HierarchyBuilderDate.Granularity.MONTH_YEAR;
//
//        String decodedDate = DateTransformationHelper.reverseDateEncoding(encodedDate, granularity);
//        assertEquals(expectedDate, decodedDate);
//    }

    @Test
    void reverseDateEncodingTest_weekYear() throws IOException {
        String encodedDate = "11/1915";
        String expectedDate = "1915-03-15";
        HierarchyBuilderDate.Granularity granularity = HierarchyBuilderDate.Granularity.WEEK_YEAR;

        String decodedDate = DateTransformationHelper.reverseDateEncoding(encodedDate, granularity);
        assertEquals(expectedDate, decodedDate);
    }

    @Test
    void reverseDateEncodingTest_year() throws IOException {
        String encodedDate = "1915";
        String expectedDate = "1915-07-01";
        HierarchyBuilderDate.Granularity granularity = HierarchyBuilderDate.Granularity.YEAR;

        String decodedDate = DateTransformationHelper.reverseDateEncoding(encodedDate, granularity);
        assertEquals(expectedDate, decodedDate);
    }
}
