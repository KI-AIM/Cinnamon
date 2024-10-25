package de.kiaim.test.platform.processor;

import de.kiaim.model.configuration.data.*;
import de.kiaim.model.data.*;
import de.kiaim.model.enumeration.DataScale;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.model.enumeration.TransformationErrorType;
import de.kiaim.platform.PlatformApplication;
import de.kiaim.platform.model.DataRowTransformationError;
import de.kiaim.platform.model.DataTransformationError;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.file.FileConfiguration;
import de.kiaim.platform.processor.CsvProcessor;
import de.kiaim.test.util.FileConfigurationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = PlatformApplication.class)
@ActiveProfiles("test")
public class DataTransformationTests {
    @Autowired
    CsvProcessor csvProcessor;

    @Test
    void testDataProcessingAndFindMissingValues() {
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
        FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(false);

        DataConfiguration config = new DataConfiguration();

        config.setConfigurations(
                List.of(
                    new ColumnConfiguration(
                            0, "test", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()
                    ),
                    new ColumnConfiguration(
                            1, "index", DataType.INTEGER, DataScale.INTERVAL, new ArrayList<>()
                    )
                )
        );

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        TransformationResult actualResult = csvProcessor.read(stream, fileConfiguration, config);

        TransformationResult expectedResult = testDataProcessingAndFindMissingValues_expectedTransformationResult();

        assertEquals(actualResult, expectedResult);

    }

    private TransformationResult testDataProcessingAndFindMissingValues_expectedTransformationResult() {
        List<DataRow> dataRows =
                List.of(
                        new DataRow(List.of(
                                new StringData("Hello"),
                                new IntegerData(1)
                        )),
                        new DataRow(List.of(
                                new StringData("This"),
                                new IntegerData(2)
                        )),
                        new DataRow(List.of(
                                new StringData("is"),
                                new IntegerData(3)
                        )),
                        new DataRow(List.of(
                                new StringData(null),
                                new IntegerData(4)
                        )),
                        new DataRow(List.of(
                                new StringData("a"),
                                new IntegerData(5)
                        )),
                        new DataRow(List.of(
                                new StringData(null),
                                new IntegerData(6)
                        )),
                        new DataRow(List.of(
                                new StringData("test"),
                                new IntegerData(7)
                        )),
                        new DataRow(List.of(
                                new StringData("for"),
                                new IntegerData(8)
                        )),
                        new DataRow(List.of(
                                new StringData(null),
                                new IntegerData(9)
                        )),
                        new DataRow(List.of(
                                new StringData("missing"),
                                new IntegerData(10)
                        )),
                        new DataRow(List.of(
                                new StringData("values"),
                                new IntegerData(11)
                        )),
                        new DataRow(List.of(
                                new StringData(null),
                                new IntegerData(12)
                        ))
                );

        DataConfiguration dataConfiguration = new DataConfiguration();
        dataConfiguration.setConfigurations(List.of(
            new ColumnConfiguration(0, "test", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()),
            new ColumnConfiguration(1, "index", DataType.INTEGER, DataScale.INTERVAL, new ArrayList<>())
        ));

        DataSet dataSet = new DataSet(dataRows, dataConfiguration);

        List<DataRowTransformationError> dataRowTransformationErrors =
            List.of(
                    new DataRowTransformationError(
                            3,
                            List.of(new DataTransformationError(0, TransformationErrorType.MISSING_VALUE, "N/A"))
                    ),
                    new DataRowTransformationError(
                            5,
                            List.of(new DataTransformationError(0, TransformationErrorType.MISSING_VALUE, "null"))
                    ),
                    new DataRowTransformationError(
                            8,
                            List.of(new DataTransformationError(0, TransformationErrorType.MISSING_VALUE, "NaN"))
                    ),
                    new DataRowTransformationError(
                            11,
                            List.of(new DataTransformationError(0, TransformationErrorType.MISSING_VALUE, ""))
                    )
            );
        return new TransformationResult(dataSet, dataRowTransformationErrors);
    }

    @Test
    void testDataProcessingAndDateFormatter() {
        String csvData =
                """
               1975-05-08
               1994-02-28
               28.03.1239
               1959-02-03
               1982-02-20
                """.trim();
        FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(false);

        DateFormatConfiguration dateFormatConfiguration = new DateFormatConfiguration();
        dateFormatConfiguration.setDateFormatter("yyyy-MM-dd");

        DataConfiguration config = new DataConfiguration();

        config.addColumnConfiguration(
                new ColumnConfiguration(
                        0, "date", DataType.DATE, DataScale.DATE, List.of(dateFormatConfiguration)
                )
        );

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        TransformationResult actualResult = csvProcessor.read(stream, fileConfiguration, config);
        TransformationResult expectedResult = testDataProcessingAndDateFormatter_expectedResult();

        assertEquals(actualResult, expectedResult);
    }

    private TransformationResult testDataProcessingAndDateFormatter_expectedResult() {
        List<DataRow> dataRows =
                List.of(
                        new DataRow(List.of(
                                new DateData(LocalDate.parse("1975-05-08"))
                        )),
                        new DataRow(List.of(
                                new DateData(LocalDate.parse("1994-02-28"))
                        )),
                        new DataRow(List.of(
                                new DateData(null)
                        )),
                        new DataRow(List.of(
                                new DateData(LocalDate.parse("1959-02-03"))
                        )),
                        new DataRow(List.of(
                                new DateData(LocalDate.parse("1982-02-20"))
                        ))
                );

        DateFormatConfiguration dateFormatConfiguration = new DateFormatConfiguration();
        dateFormatConfiguration.setDateFormatter("yyyy-MM-dd");

        DataConfiguration dataConfiguration = new DataConfiguration();
        dataConfiguration.setConfigurations(List.of(
                new ColumnConfiguration(0, "date", DataType.DATE, DataScale.DATE, List.of(dateFormatConfiguration))
        ));

        DataSet dataSet = new DataSet(dataRows, dataConfiguration);

        List<DataRowTransformationError> dataRowTransformationErrors =
                List.of(
                        new DataRowTransformationError(
                                2,
                                List.of(new DataTransformationError(0, TransformationErrorType.FORMAT_ERROR, "28.03.1239"))
                        )
                );
        return new TransformationResult(dataSet, dataRowTransformationErrors);
    }

    @Test
    void testDataProcessingAndDateRange() {
        String csvData =
                """
               2000-01-01
               2000-01-02
               2000-12-31
               2001-01-01
                """.trim();
        FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(false);

        RangeConfiguration rangeConfiguration = new RangeConfiguration(new DateData(LocalDate.of(2000, 1, 2)),
                                                                       new DateData(LocalDate.of(2000, 12, 31))
        );

        DataConfiguration config = new DataConfiguration();

        config.addColumnConfiguration(
                new ColumnConfiguration(
                        0, "date", DataType.DATE, DataScale.INTERVAL, List.of(rangeConfiguration)
                )
        );

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        TransformationResult actualResult = csvProcessor.read(stream, fileConfiguration, config);
        TransformationResult expectedResult = testDataProcessingAndDateRange_expectedResult();

        assertEquals(actualResult, expectedResult);
    }

    private TransformationResult testDataProcessingAndDateRange_expectedResult() {
        List<DataRow> dataRows = List.of(
                new DataRow(List.of(
                        new DateData(null)
                )),
                new DataRow(List.of(
                        new DateData(LocalDate.parse("2000-01-02"))
                )),
                new DataRow(List.of(
                        new DateData(LocalDate.parse("2000-12-31"))
                )),
                new DataRow(List.of(
                        new DateData(null)
                ))
        );

        RangeConfiguration rangeConfiguration = new RangeConfiguration(new DateData(LocalDate.of(2000, 1, 2)),
                                                                       new DateData(LocalDate.of(2000, 12, 31))
        );

        DataConfiguration dataConfiguration = new DataConfiguration();
        dataConfiguration.setConfigurations(List.of(
                new ColumnConfiguration(0, "date", DataType.DATE, DataScale.INTERVAL, List.of(rangeConfiguration))
        ));

        DataSet dataSet = new DataSet(dataRows, dataConfiguration);

        List<DataRowTransformationError> dataRowTransformationErrors =
                List.of(
                        new DataRowTransformationError(
                                0,
                                List.of(new DataTransformationError(0, TransformationErrorType.VALUE_NOT_IN_RANGE, "2000-01-01"))
                        ),
                        new DataRowTransformationError(
                                3,
                                List.of(new DataTransformationError(0, TransformationErrorType.VALUE_NOT_IN_RANGE, "2001-01-01"))
                        )
                );
        return new TransformationResult(dataSet, dataRowTransformationErrors);
    }

    @Test
    void testDataProcessingAndDateTimeFormatter() {
        String csvData =
                """
               1975-05-08T02:32:23
               1994-02-28T12:42:42
               1239-03-28T05:23:42
               1959-02-03T13:01:52
               1982-02-20T2321:24:34018
                """.trim();
        FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(false);

        DateTimeFormatConfiguration dateTimeFormatConfiguration = new DateTimeFormatConfiguration("yyyy-MM-dd'T'HH:mm:ss");

        DataConfiguration config = new DataConfiguration();

        config.addColumnConfiguration(
                new ColumnConfiguration(
                        0, "date-time", DataType.DATE_TIME, DataScale.DATE, List.of(dateTimeFormatConfiguration)
                )
        );

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        TransformationResult actualResult = csvProcessor.read(stream, fileConfiguration, config);
        TransformationResult expectedResult = testDataProcessingAndDateTimeFormatter_expectedResult();

        assertEquals(actualResult, expectedResult);
    }

    private TransformationResult testDataProcessingAndDateTimeFormatter_expectedResult() {
        List<DataRow> dataRows =
                List.of(
                        new DataRow(List.of(
                                new DateTimeData(LocalDateTime.parse("1975-05-08T02:32:23"))
                        )),
                        new DataRow(List.of(
                                new DateTimeData(LocalDateTime.parse("1994-02-28T12:42:42"))
                        )),
                        new DataRow(List.of(
                                new DateTimeData(LocalDateTime.parse("1239-03-28T05:23:42"))
                        )),
                        new DataRow(List.of(
                                new DateTimeData(LocalDateTime.parse("1959-02-03T13:01:52"))
                        )),
                        new DataRow(List.of(
                                new DateTimeData(null)
                        ))
                );

        DateTimeFormatConfiguration dateTimeFormatConfiguration = new DateTimeFormatConfiguration();
        dateTimeFormatConfiguration.setDateTimeFormatter("yyyy-MM-dd'T'HH:mm:ss");

        DataConfiguration dataConfiguration = new DataConfiguration();
        dataConfiguration.setConfigurations(List.of(
                new ColumnConfiguration(0, "date-time", DataType.DATE_TIME, DataScale.DATE, List.of(dateTimeFormatConfiguration))
        ));

        DataSet dataSet = new DataSet(dataRows, dataConfiguration);

        List<DataRowTransformationError> dataRowTransformationErrors =
                List.of(
                        new DataRowTransformationError(
                                4,
                                List.of(new DataTransformationError(0, TransformationErrorType.FORMAT_ERROR, "1982-02-20T2321:24:34018"))
                        )
                );
        return new TransformationResult(dataSet, dataRowTransformationErrors);
    }

    @Test
    void testDataProcessingAndDateTimeRange() {
        String csvData =
                """
               2000-01-01T12:31:30
               2000-01-01T12:31:31
               2000-12-31T12:31:31
               2000-12-31T12:31:32
                """.trim();
        FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(false);

        RangeConfiguration rangeConfiguration = new RangeConfiguration(new DateTimeData(LocalDateTime.of(2000, 1, 1, 12, 31, 31)), new DateTimeData(LocalDateTime.of(2000, 12, 31, 12, 31, 31)));

        DataConfiguration config = new DataConfiguration();

        config.addColumnConfiguration(
                new ColumnConfiguration(
                        0, "date-time", DataType.DATE_TIME, DataScale.INTERVAL, List.of(rangeConfiguration)
                )
        );

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        TransformationResult actualResult = csvProcessor.read(stream, fileConfiguration, config);
        TransformationResult expectedResult = testDataProcessingAndDateTimeRange_expectedResult();

        assertEquals(actualResult, expectedResult);
    }

    private TransformationResult testDataProcessingAndDateTimeRange_expectedResult() {
        List<DataRow> dataRows = List.of(
                new DataRow(List.of(
                        new DateTimeData(null)
                )),
                new DataRow(List.of(
                        new DateTimeData(LocalDateTime.parse("2000-01-01T12:31:31"))
                )),
                new DataRow(List.of(
                        new DateTimeData(LocalDateTime.parse("2000-12-31T12:31:31"))
                )),
                new DataRow(List.of(
                        new DateTimeData(null)
                ))
        );

        RangeConfiguration rangeConfiguration = new RangeConfiguration(
                new DateTimeData(LocalDateTime.of(2000, 1, 1, 12, 31, 31)),
                new DateTimeData(LocalDateTime.of(2000, 12, 31, 12, 31, 31))
        );

        DataConfiguration dataConfiguration = new DataConfiguration();
        dataConfiguration.setConfigurations(List.of(
                new ColumnConfiguration(0, "date-time", DataType.DATE_TIME, DataScale.INTERVAL, List.of(rangeConfiguration))
        ));

        DataSet dataSet = new DataSet(dataRows, dataConfiguration);

        List<DataRowTransformationError> dataRowTransformationErrors =
                List.of(
                        new DataRowTransformationError(
                                0,
                                List.of(new DataTransformationError(0, TransformationErrorType.VALUE_NOT_IN_RANGE, "2000-01-01T12:31:30"))
                        ),
                        new DataRowTransformationError(
                                3,
                                List.of(new DataTransformationError(0, TransformationErrorType.VALUE_NOT_IN_RANGE, "2000-12-31T12:31:32"))
                        )
                );
        return new TransformationResult(dataSet, dataRowTransformationErrors);
    }

    @Test
    void testDataProcessingAndStringPattern() {
        String csvData =
                """
                A124.21
                V321.23
                X421.23
                C531.12
                Wrong pattern
                """.trim();
        FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(false);

        DataConfiguration config = new DataConfiguration();
        StringPatternConfiguration stringPatternConfiguration = new StringPatternConfiguration("[A-Z]\\d{3}[.]\\d{2}");

        config.addColumnConfiguration(
                new ColumnConfiguration(
                        0, "string-pattern", DataType.STRING, DataScale.NOMINAL, List.of(stringPatternConfiguration)
                )
        );

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        TransformationResult actualResult = csvProcessor.read(stream, fileConfiguration, config);
        TransformationResult expectedResult = testDataProcessingAndStringPattern_expectedResult();

        assertEquals(actualResult, expectedResult);
    }

    private TransformationResult testDataProcessingAndStringPattern_expectedResult() {
        List<DataRow> dataRows =
                List.of(
                        new DataRow(List.of(
                                new StringData("A124.21")
                        )),
                        new DataRow(List.of(
                                new StringData("V321.23")
                        )),
                        new DataRow(List.of(
                                new StringData("X421.23")
                        )),
                        new DataRow(List.of(
                                new StringData("C531.12")
                        )),
                        new DataRow(List.of(
                                new StringData(null)
                        ))
                );

        DataConfiguration dataConfiguration = new DataConfiguration();
        StringPatternConfiguration stringPatternConfiguration = new StringPatternConfiguration("[A-Z]\\d{3}[.]\\d{2}");

        dataConfiguration.setConfigurations(List.of(
                new ColumnConfiguration(0, "string-pattern", DataType.STRING, DataScale.NOMINAL, List.of(stringPatternConfiguration))
        ));

        DataSet dataSet = new DataSet(dataRows, dataConfiguration);

        List<DataRowTransformationError> dataRowTransformationErrors =
                List.of(
                        new DataRowTransformationError(
                                4,
                                List.of(new DataTransformationError(0, TransformationErrorType.FORMAT_ERROR, "Wrong pattern"))
                        )
                );
        return new TransformationResult(dataSet, dataRowTransformationErrors);
    }

    @Test
    void testDataProcessingAndBooleanFormats() {
        String csvData =
                """
               yes
               no
               1
               0
               No boolean value
                """.trim();
        FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(false);

        DataConfiguration config = new DataConfiguration();

        config.addColumnConfiguration(
                new ColumnConfiguration(
                        0, "boolean", DataType.BOOLEAN, DataScale.NOMINAL, new ArrayList<>()
                )
        );

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        TransformationResult actualResult = csvProcessor.read(stream, fileConfiguration, config);
        TransformationResult expectedResult = testDataProcessingAndBooleanFormats_expectedResult();

        assertEquals(actualResult, expectedResult);
    }

    private TransformationResult testDataProcessingAndBooleanFormats_expectedResult() {
        List<DataRow> dataRows =
                List.of(
                        new DataRow(List.of(
                                new BooleanData(true)
                        )),
                        new DataRow(List.of(
                                new BooleanData(false)
                        )),
                        new DataRow(List.of(
                                new BooleanData(true)
                        )),
                        new DataRow(List.of(
                                new BooleanData(false)
                        )),
                        new DataRow(List.of(
                                new BooleanData(null)
                        ))
                );

        DataConfiguration dataConfiguration = new DataConfiguration();

        dataConfiguration.setConfigurations(List.of(
                new ColumnConfiguration(0, "boolean", DataType.BOOLEAN, DataScale.NOMINAL, List.of())
        ));

        DataSet dataSet = new DataSet(dataRows, dataConfiguration);

        List<DataRowTransformationError> dataRowTransformationErrors =
                List.of(
                        new DataRowTransformationError(
                                4,
                                List.of(new DataTransformationError(0, TransformationErrorType.FORMAT_ERROR, "No boolean value"))
                        )
                );
        return new TransformationResult(dataSet, dataRowTransformationErrors);
    }

    @Test
    void testDataProcessingAndFloatFormat() {
        String csvData =
                """
               1.23
               3.242
               4.534
               5.7664
               No float value
                """.trim();
        FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(false);

        DataConfiguration config = new DataConfiguration();

        config.addColumnConfiguration(
                new ColumnConfiguration(
                        0, "float", DataType.DECIMAL, DataScale.RATIO, new ArrayList<>()
                )
        );

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        TransformationResult actualResult = csvProcessor.read(stream, fileConfiguration, config);
        TransformationResult expectedResult = testDataProcessingAndFloatFormat_expectedResult();

        assertEquals(actualResult, expectedResult);
    }

    private TransformationResult testDataProcessingAndFloatFormat_expectedResult() {
        List<DataRow> dataRows =
                List.of(
                        new DataRow(List.of(
                                new DecimalData(1.23f)
                        )),
                        new DataRow(List.of(
                                new DecimalData(3.242f)
                        )),
                        new DataRow(List.of(
                                new DecimalData(4.534f)
                        )),
                        new DataRow(List.of(
                                new DecimalData(5.7664f)
                        )),
                        new DataRow(List.of(
                                new DecimalData(null)
                        ))
                );

        DataConfiguration dataConfiguration = new DataConfiguration();

        dataConfiguration.setConfigurations(List.of(
                new ColumnConfiguration(0, "float", DataType.DECIMAL, DataScale.RATIO, List.of())
        ));

        DataSet dataSet = new DataSet(dataRows, dataConfiguration);

        List<DataRowTransformationError> dataRowTransformationErrors =
                List.of(
                        new DataRowTransformationError(
                                4,
                                List.of(new DataTransformationError(0, TransformationErrorType.FORMAT_ERROR, "No float value"))
                        )
                );
        return new TransformationResult(dataSet, dataRowTransformationErrors);
    }

    @Test
    void testDataProcessingAndFloatRange() {
        String csvData =
                """
               0.9999
               1.0
               1.3
               1.5
               1.500001
                """.trim();
        FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(false);

        RangeConfiguration rangeConfiguration = new RangeConfiguration(new DecimalData(1f), new DecimalData(1.5f));

        DataConfiguration config = new DataConfiguration();
        config.addColumnConfiguration(
                new ColumnConfiguration(
                        0, "decimal", DataType.DECIMAL, DataScale.RATIO, List.of(rangeConfiguration)
                )
        );

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        TransformationResult actualResult = csvProcessor.read(stream, fileConfiguration, config);
        TransformationResult expectedResult = testDataProcessingAndFloatRange_expectedResult();

        assertEquals(actualResult, expectedResult);
    }

    private TransformationResult testDataProcessingAndFloatRange_expectedResult() {
        List<DataRow> dataRows = List.of(
                new DataRow(List.of(new DecimalData(null))),
                new DataRow(List.of(new DecimalData(1.0f))),
                new DataRow(List.of(new DecimalData(1.3f))),
                new DataRow(List.of(new DecimalData(1.5f))),
                new DataRow(List.of(new DecimalData(null)))
        );

        RangeConfiguration rangeConfiguration = new RangeConfiguration(new DecimalData(1f), new DecimalData(1.5f));

        DataConfiguration dataConfiguration = new DataConfiguration();
        dataConfiguration.addColumnConfiguration(
                new ColumnConfiguration(
                        0, "decimal", DataType.DECIMAL, DataScale.RATIO, List.of(rangeConfiguration)
                )
        );

        DataSet dataSet = new DataSet(dataRows, dataConfiguration);

        List<DataRowTransformationError> dataRowTransformationErrors =
                List.of(
                        new DataRowTransformationError(
                                0,
                                List.of(new DataTransformationError(0, TransformationErrorType.VALUE_NOT_IN_RANGE, "0.9999"))
                        ),
                        new DataRowTransformationError(
                                4,
                                List.of(new DataTransformationError(0, TransformationErrorType.VALUE_NOT_IN_RANGE, "1.500001"))
                        )
                );
        return new TransformationResult(dataSet, dataRowTransformationErrors);
    }

    @Test
    void testDataProcessingAndIntegerFormat() {
        String csvData =
                """
               1
               2
               3
               4
               No int value
                """.trim();
        FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(false);

        DataConfiguration config = new DataConfiguration();

        config.addColumnConfiguration(
                new ColumnConfiguration(
                        0, "int", DataType.INTEGER, DataScale.INTERVAL, new ArrayList<>()
                )
        );

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        TransformationResult actualResult = csvProcessor.read(stream, fileConfiguration, config);
        TransformationResult expectedResult = testDataProcessingAndIntegerFormat_expectedResult();

        assertEquals(actualResult, expectedResult);
    }

    private TransformationResult testDataProcessingAndIntegerFormat_expectedResult() {
        List<DataRow> dataRows =
                List.of(
                        new DataRow(List.of(
                                new IntegerData(1)
                        )),
                        new DataRow(List.of(
                                new IntegerData(2)
                        )),
                        new DataRow(List.of(
                                new IntegerData(3)
                        )),
                        new DataRow(List.of(
                                new IntegerData(4)
                        )),
                        new DataRow(List.of(
                                new IntegerData(null)
                        ))
                );

        DataConfiguration dataConfiguration = new DataConfiguration();

        dataConfiguration.setConfigurations(List.of(
                new ColumnConfiguration(0, "int", DataType.INTEGER, DataScale.INTERVAL, List.of())
        ));

        DataSet dataSet = new DataSet(dataRows, dataConfiguration);

        List<DataRowTransformationError> dataRowTransformationErrors =
                List.of(
                        new DataRowTransformationError(
                                4,
                                List.of(new DataTransformationError(0, TransformationErrorType.FORMAT_ERROR, "No int value"))
                        )
                );
        return new TransformationResult(dataSet, dataRowTransformationErrors);
    }

    @Test
    void testDataProcessingAndIntegerRange() {
        String csvData =
                """
                1
                2
                3
                4
                5
                """.trim();
        FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(false);

        RangeConfiguration rangeConfiguration = new RangeConfiguration(new IntegerData(2), new IntegerData(4));

        DataConfiguration config = new DataConfiguration();
        config.addColumnConfiguration(
                new ColumnConfiguration(
                        0, "integer", DataType.INTEGER, DataScale.INTERVAL, List.of(rangeConfiguration)
                )
        );

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        TransformationResult actualResult = csvProcessor.read(stream, fileConfiguration, config);
        TransformationResult expectedResult = testDataProcessingAndIntegerRange_expectedResult();

        assertEquals(actualResult, expectedResult);
    }

    private TransformationResult testDataProcessingAndIntegerRange_expectedResult() {
        List<DataRow> dataRows = List.of(
                new DataRow(List.of(new IntegerData(null))),
                new DataRow(List.of(new IntegerData(2))),
                new DataRow(List.of(new IntegerData(3))),
                new DataRow(List.of(new IntegerData(4))),
                new DataRow(List.of(new IntegerData(null)))
        );

        RangeConfiguration rangeConfiguration = new RangeConfiguration(new IntegerData(2), new IntegerData(4));

        DataConfiguration dataConfiguration = new DataConfiguration();
        dataConfiguration.addColumnConfiguration(
                new ColumnConfiguration(
                        0, "integer", DataType.INTEGER, DataScale.INTERVAL, List.of(rangeConfiguration)
                )
        );

        DataSet dataSet = new DataSet(dataRows, dataConfiguration);

        List<DataRowTransformationError> dataRowTransformationErrors =
                List.of(
                        new DataRowTransformationError(
                                0,
                                List.of(new DataTransformationError(0, TransformationErrorType.VALUE_NOT_IN_RANGE, "1"))
                        ),
                        new DataRowTransformationError(
                                4,
                                List.of(new DataTransformationError(0, TransformationErrorType.VALUE_NOT_IN_RANGE, "5"))
                        )
                );
        return new TransformationResult(dataSet, dataRowTransformationErrors);
    }
}
