package de.kiaim.cinnamon.test.platform.processor;


import de.kiaim.cinnamon.model.configuration.data.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.configuration.data.DateFormatConfiguration;
import de.kiaim.cinnamon.model.data.*;
import de.kiaim.cinnamon.model.enumeration.DataScale;
import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.platform.PlatformApplication;
import de.kiaim.cinnamon.platform.model.TransformationResult;
import de.kiaim.cinnamon.platform.model.entity.FileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.cinnamon.platform.model.file.FileType;
import de.kiaim.cinnamon.platform.processor.XlsxProcessor;
import de.kiaim.cinnamon.test.util.DataSetTestHelper;
import de.kiaim.cinnamon.test.util.FileConfigurationTestHelper;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = PlatformApplication.class)
@ActiveProfiles("test")
public class XLSXProcessingTests {

    @Autowired
    XlsxProcessor xlsxProcessor;


    @Test
    void testReadMethodOfXlsxProcessor() throws FileNotFoundException {
        InputStream stream = new FileInputStream(
            new File("src/test/resources/xlsx_test.xlsx")
        );

        FileConfigurationEntity fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(FileType.XLSX, false);
        DataConfiguration config = getDataConfiguration();

        TransformationResult actualResult = assertDoesNotThrow(
            () -> xlsxProcessor.read(stream, fileConfiguration, config)
        );

        TransformationResult expectedResult = testReadMethodOfXlsxProcessor_getExpectedTransformationResult();

        assertEquals(actualResult, expectedResult);
    }

    @Test
    void testReadMethodOfXlsxProcessorWithHeader() throws FileNotFoundException {
        InputStream stream = new FileInputStream(
            new File("src/test/resources/xlsx_test_with_header.xlsx")
        );

        FileConfigurationEntity fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(FileType.XLSX, true);
        DataConfiguration config = getDataConfiguration();

        TransformationResult actualResult = assertDoesNotThrow(
            () -> xlsxProcessor.read(stream, fileConfiguration, config)
        );

        TransformationResult expectedResult = testReadMethodOfXlsxProcessor_getExpectedTransformationResult();

        assertEquals(actualResult, expectedResult);
    }


    private TransformationResult testReadMethodOfXlsxProcessor_getExpectedTransformationResult() {
        List<DataRow> dataRows =
            List.of(
                new DataRow(List.of(
                    new IntegerData(650390),
                    new StringData("Tonisha Swift"),
                    new DateData(LocalDate.parse("1975-05-08")),
                    new BooleanData(false),
                    new StringData("303.23 €")
                )),
                new DataRow(List.of(
                    new IntegerData(208589),
                    new StringData("Wilson Maggio"),
                    new DateData(LocalDate.parse("1994-02-28")),
                    new BooleanData(false),
                    new StringData("23623.18 €")
                )),
                new DataRow(List.of(
                    new IntegerData(452159),
                    new StringData("Bill Hintz"),
                    new DateData(LocalDate.parse("1987-05-17")),
                    new BooleanData(false),
                    new StringData("38.41 €")
                )),
                new DataRow(List.of(
                    new IntegerData(730160),
                    new StringData("Heathcote, Nelia"),
                    new DateData(LocalDate.parse("1959-02-03")),
                    new BooleanData(true),
                    new StringData("21.01 €")
                )),
                new DataRow(List.of(
                    new IntegerData(614164),
                    new StringData("Ms. Chester Keebler"),
                    new DateData(LocalDate.parse("1982-02-20")),
                    new BooleanData(false),
                    new StringData("158.79 €")
                ))
            );

        DataConfiguration dataConfiguration = getDataConfiguration();

        DataSet dataSet = new DataSet(dataRows, dataConfiguration);

        return new TransformationResult(dataSet, new ArrayList<>());
    }


    @Test
    void testEstimationMostEstimated() throws FileNotFoundException {
        InputStream stream = new FileInputStream(
                new File("src/test/resources/xlsx_test_mixed_datatype.xlsx")
        );

        FileConfigurationEntity fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(FileType.XLSX, false);

        DataConfiguration actualConfiguration = xlsxProcessor.estimateDataConfiguration(stream, fileConfiguration,
                                                                                        DatatypeEstimationAlgorithm.MOST_ESTIMATED);

        DataConfiguration expectedConfiguration = getEstimationDataConfiguration();

        List<DataType> expectedDatatypes = expectedConfiguration.getDataTypes();
        List<DataType> actualDatatypes = actualConfiguration.getDataTypes();

        assertEquals(expectedDatatypes, actualDatatypes);
    }

    @Test
    void testEstimationMostGeneral() throws FileNotFoundException {
        InputStream stream = new FileInputStream(
                new File("src/test/resources/xlsx_test_mixed_datatype.xlsx")
        );

        FileConfigurationEntity fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(FileType.XLSX, false);

        DataConfiguration actualConfiguration = xlsxProcessor.estimateDataConfiguration(stream, fileConfiguration,
                                                                                        DatatypeEstimationAlgorithm.MOST_GENERAL);

        DataConfiguration expectedConfiguration = getEstimationDataConfiguration();

        List<DataType> expectedDatatypes = expectedConfiguration.getDataTypes();
        expectedDatatypes.set(0, DataType.STRING);
        List<DataType> actualDatatypes = actualConfiguration.getDataTypes();

        assertEquals(expectedDatatypes, actualDatatypes);
    }

    @Test
    void testEstimationHeaders() throws FileNotFoundException {
        InputStream stream = new FileInputStream(
            new File("src/test/resources/xlsx_test_with_header.xlsx")
        );

        FileConfigurationEntity fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(FileType.XLSX, true);

        DataConfiguration actualConfiguration = xlsxProcessor.estimateDataConfiguration(stream, fileConfiguration,
                                                                                        DatatypeEstimationAlgorithm.MOST_ESTIMATED);

        DataConfiguration expectedConfiguration = getEstimationDataConfiguration();

        List<DataType> expectedDatatypes = expectedConfiguration.getDataTypes();
        List<DataType> actualDatatypes = actualConfiguration.getDataTypes();

        assertEquals(expectedDatatypes, actualDatatypes);
        assertEquals(expectedConfiguration, actualConfiguration);
    }

    @Test
    void testWrite() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        DataSet dataset = DataSetTestHelper.generateDataSet();

        assertDoesNotThrow(() -> xlsxProcessor.write(stream, dataset));

        byte[] data = stream.toByteArray();

        try(InputStream is = new ByteArrayInputStream(data); ReadableWorkbook wb = new ReadableWorkbook(is)) {
            Sheet sheet = wb.getFirstSheet();
            List<Row> rows = sheet.read();

            assertEquals(dataset.getDataRows().size() + 1, rows.size(), "Number of rows does not match!");

            Row headerRow = rows.get(0);
            for (int columnIndex = 0; columnIndex < dataset.getDataConfiguration().getColumnNames().size(); columnIndex++) {
                assertEquals(dataset.getDataConfiguration().getColumnNames().get(columnIndex), headerRow.getCell(columnIndex).asString(), "Column names do not match!");
            }

            for (int rowIndex = 0; rowIndex < dataset.getDataRows().size(); rowIndex++) {
                DataRow dataRow = dataset.getDataRows().get(rowIndex);
                Row row = rows.get(rowIndex + 1);

                for (int columnIndex = 0; columnIndex < dataRow.getData().size(); columnIndex++) {
                    Data expectedValue = dataRow.getData().get(columnIndex);
                    Cell actualValue = row.getCell(columnIndex);

                    if (expectedValue instanceof BooleanData booleanData) {
                        assertEquals(booleanData.getValue(), actualValue.asBoolean(), "Values do not match!");
                    } else if (expectedValue instanceof DateData dateData) {
                        assertEquals(dateData.getValue(), actualValue.asDate().toLocalDate(), "Values do not match!");
                    } else if (expectedValue instanceof DateTimeData dateTimeData) {
                        assertEquals(dateTimeData.getValue().minus(456, ChronoUnit.MICROS), actualValue.asDate(), "Values do not match!");
                    } else if (expectedValue instanceof DecimalData decimalData) {
                        assertEquals(decimalData.getValue(), actualValue.asNumber().floatValue(), "Values do not match!");
                    } else if (expectedValue instanceof IntegerData integerData) {
                        assertEquals(integerData.getValue(), actualValue.asNumber().intValue(), "Values do not match!");
                    } else if (expectedValue instanceof StringData stringData) {
                        assertEquals(stringData.getValue(), actualValue.asString(), "Values do not match!");
                    }
                }
            }

        } catch (IOException e) {
            fail(e);
        }
    }

    private static DataConfiguration getDataConfiguration() {
        DataConfiguration config = new DataConfiguration();

        ColumnConfiguration column1 = new ColumnConfiguration(
            0, "id", DataType.INTEGER, DataScale.INTERVAL, new ArrayList<>()
        );

        ColumnConfiguration column2 = new ColumnConfiguration(
            1, "name", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()
        );

        ColumnConfiguration column3 = new ColumnConfiguration(
            2, "birthdate", DataType.DATE, DataScale.DATE, List.of(new DateFormatConfiguration("yyyy-MM-dd"))
        );

        ColumnConfiguration column4 = new ColumnConfiguration(
            3, "smoker", DataType.BOOLEAN, DataScale.NOMINAL, new ArrayList<>()
        );

        ColumnConfiguration column5 = new ColumnConfiguration(
            4, "price", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()
        );

        config.setConfigurations(List.of(column1, column2, column3, column4, column5));
        return config;
    }

    private static DataConfiguration getEstimationDataConfiguration() {
        DataConfiguration config = new DataConfiguration();

        ColumnConfiguration column1 = new ColumnConfiguration(
            0, "id", DataType.INTEGER, DataScale.INTERVAL, new ArrayList<>()
        );

        ColumnConfiguration column2 = new ColumnConfiguration(
            1, "name", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()
        );

        ColumnConfiguration column3 = new ColumnConfiguration(
            2, "birthdate", DataType.INTEGER, DataScale.INTERVAL, new ArrayList<>()
        );

        ColumnConfiguration column4 = new ColumnConfiguration(
            3, "smoker", DataType.BOOLEAN, DataScale.NOMINAL, new ArrayList<>()
        );

        ColumnConfiguration column5 = new ColumnConfiguration(
            4, "price", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()
        );

        config.setConfigurations(List.of(column1, column2, column3, column4, column5));
        return config;
    }

}
