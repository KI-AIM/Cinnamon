package de.kiaim.platform;

import de.kiaim.platform.model.TransformationErrorType;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.data.DataType;
import de.kiaim.platform.model.data.configuration.*;
import de.kiaim.platform.processor.CsvProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = PlatformApplication.class)
@ActiveProfiles("test")
public class DataTransformationTests {
    @Autowired
    CsvProcessor csvProcessor;

    @Test
    void testMissingValue() {
        String csvData = """
                Hello,1
                This,2
                is,3
                N/A,4
                a,5
                null,6
                test,7
                for,8
                NaN,9
                missing,10
                values,11
                ,12
                """;

        DataConfiguration config = new DataConfiguration();

        config.setConfigurations(
                List.of(
                    new ColumnConfiguration(
                            0, "test", DataType.STRING, new ArrayList<>()
                    ),
                    new ColumnConfiguration(
                            1, "index", DataType.INTEGER, new ArrayList<>()
                    )
                )
        );

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        TransformationResult readResult = csvProcessor.read(stream, config);

        assert !readResult.getTransformationErrors().isEmpty();

        assert readResult.getTransformationErrors().get(0).getIndex() == 3;
        assert !readResult.getTransformationErrors().get(0).getDataTransformationErrors().isEmpty();
        assert readResult.getTransformationErrors().get(0).getDataTransformationErrors().get(0).getErrorType().equals(TransformationErrorType.MISSING_VALUE);
        assert !readResult.getTransformationErrors().get(0).getRawValues().isEmpty();
        assert readResult.getTransformationErrors().get(0).getRawValues().get(0).equals("N/A");

        assert readResult.getTransformationErrors().get(1).getIndex() == 5;
        assert !readResult.getTransformationErrors().get(1).getDataTransformationErrors().isEmpty();
        assert readResult.getTransformationErrors().get(1).getDataTransformationErrors().get(0).getErrorType().equals(TransformationErrorType.MISSING_VALUE);
        assert !readResult.getTransformationErrors().get(1).getRawValues().isEmpty();
        assert readResult.getTransformationErrors().get(1).getRawValues().get(0).equals("null");

        assert readResult.getTransformationErrors().get(2).getIndex() == 8;
        assert !readResult.getTransformationErrors().get(2).getDataTransformationErrors().isEmpty();
        assert readResult.getTransformationErrors().get(2).getDataTransformationErrors().get(0).getErrorType().equals(TransformationErrorType.MISSING_VALUE);
        assert !readResult.getTransformationErrors().get(2).getRawValues().isEmpty();
        assert readResult.getTransformationErrors().get(2).getRawValues().get(0).equals("NaN");

        assert readResult.getTransformationErrors().get(3).getIndex() == 11;
        assert !readResult.getTransformationErrors().get(3).getDataTransformationErrors().isEmpty();
        assert readResult.getTransformationErrors().get(3).getDataTransformationErrors().get(0).getErrorType().equals(TransformationErrorType.MISSING_VALUE);
        assert !readResult.getTransformationErrors().get(3).getRawValues().isEmpty();
        assert readResult.getTransformationErrors().get(3).getRawValues().get(0).isEmpty();
    }

    @Test
    void testDateFormat() {
        String csvData =
                """
               1975-05-08
               1994-02-28
               28.03.1239
               1959-02-03
               1982-02-20
                """.trim();

        DateFormatConfiguration dateFormatConfiguration = new DateFormatConfiguration();
        dateFormatConfiguration.setDateFormatter(DateTimeFormatter.ISO_LOCAL_DATE);

        DataConfiguration config = new DataConfiguration();

        config.addColumnConfiguration(
                new ColumnConfiguration(
                        0, "date", DataType.DATE, List.of(dateFormatConfiguration)
                )
        );

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        TransformationResult readResult = csvProcessor.read(stream, config);

        assert !readResult.getTransformationErrors().isEmpty();
        assert readResult.getTransformationErrors().get(0).getIndex() == 2;
        assert readResult.getTransformationErrors().get(0).getRawValues().get(0).equals("28.03.1239");
        assert !readResult.getTransformationErrors().get(0).getDataTransformationErrors().isEmpty();
        assert readResult.getTransformationErrors().get(0).getDataTransformationErrors().get(0).getErrorType().equals(TransformationErrorType.FORMAT_ERROR);
    }

    @Test
    void testDateTimeFormat() {
        String csvData =
                """
               1975-05-08T02:32:23
               1994-02-28T12:42:42
               1239-03-28T05:23:42
               1959-02-03T13:01:52
               1982-02-20T2321:24:34018
                """.trim();

        DateTimeFormatConfiguration dateTimeFormatConfiguration = new DateTimeFormatConfiguration(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        DataConfiguration config = new DataConfiguration();

        config.addColumnConfiguration(
                new ColumnConfiguration(
                        0, "date-time", DataType.DATE_TIME, List.of(dateTimeFormatConfiguration)
                )
        );

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        TransformationResult readResult = csvProcessor.read(stream, config);

        assert !readResult.getTransformationErrors().isEmpty();
        assert readResult.getTransformationErrors().get(0).getIndex() == 4;
        assert readResult.getTransformationErrors().get(0).getRawValues().get(0).equals("1982-02-20T2321:24:34018");
        assert !readResult.getTransformationErrors().get(0).getDataTransformationErrors().isEmpty();
        assert readResult.getTransformationErrors().get(0).getDataTransformationErrors().get(0).getErrorType().equals(TransformationErrorType.FORMAT_ERROR);
    }

    @Test
    void testStringPattern() {
        String csvData =
                """
                A124.21
                V321.23
                X421.23
                C531.12
                Wrong pattern
                """.trim();

        DataConfiguration config = new DataConfiguration();
        StringPatternConfiguration stringPatternConfiguration = new StringPatternConfiguration("[A-Z]\\d{3}[.]\\d{2}");

        config.addColumnConfiguration(
                new ColumnConfiguration(
                        0, "string-pattern", DataType.STRING, List.of(stringPatternConfiguration)
                )
        );

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        TransformationResult readResult = csvProcessor.read(stream, config);

        assert !readResult.getTransformationErrors().isEmpty();
        assert readResult.getTransformationErrors().get(0).getIndex() == 4;
        assert readResult.getTransformationErrors().get(0).getRawValues().get(0).equals("Wrong pattern");
        assert readResult.getTransformationErrors().get(0).getDataTransformationErrors().get(0).getErrorType().equals(TransformationErrorType.FORMAT_ERROR);
    }

    @Test
    void testBoolean() {
        String csvData =
                """
               yes
               no
               1
               0
               No boolean value
                """.trim();

        DataConfiguration config = new DataConfiguration();

        config.addColumnConfiguration(
                new ColumnConfiguration(
                        0, "boolean", DataType.BOOLEAN, new ArrayList<>()
                )
        );

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        TransformationResult readResult = csvProcessor.read(stream, config);

        assert !readResult.getTransformationErrors().isEmpty();
        assert readResult.getTransformationErrors().get(0).getIndex() == 4;
        assert readResult.getTransformationErrors().get(0).getRawValues().get(0).equals("No boolean value");
        assert readResult.getTransformationErrors().get(0).getDataTransformationErrors().get(0).getErrorType().equals(TransformationErrorType.FORMAT_ERROR);
    }

    @Test
    void testFloat() {
        String csvData =
                """
               1.23
               3.242
               4.534
               5.7664
               No float value
                """.trim();

        DataConfiguration config = new DataConfiguration();

        config.addColumnConfiguration(
                new ColumnConfiguration(
                        0, "float", DataType.DECIMAL, new ArrayList<>()
                )
        );

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        TransformationResult readResult = csvProcessor.read(stream, config);

        assert !readResult.getTransformationErrors().isEmpty();
        assert readResult.getTransformationErrors().get(0).getIndex() == 4;
        assert readResult.getTransformationErrors().get(0).getRawValues().get(0).equals("No float value");
        assert readResult.getTransformationErrors().get(0).getDataTransformationErrors().get(0).getErrorType().equals(TransformationErrorType.FORMAT_ERROR);
    }

    @Test
    void testInteger() {
        String csvData =
                """
               1
               2
               3
               4
               No int value
                """.trim();

        DataConfiguration config = new DataConfiguration();

        config.addColumnConfiguration(
                new ColumnConfiguration(
                        0, "int", DataType.INTEGER, new ArrayList<>()
                )
        );

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        TransformationResult readResult = csvProcessor.read(stream, config);

        assert !readResult.getTransformationErrors().isEmpty();
        assert readResult.getTransformationErrors().get(0).getIndex() == 4;
        assert readResult.getTransformationErrors().get(0).getRawValues().get(0).equals("No int value");
        assert readResult.getTransformationErrors().get(0).getDataTransformationErrors().get(0).getErrorType().equals(TransformationErrorType.FORMAT_ERROR);
    }
}
