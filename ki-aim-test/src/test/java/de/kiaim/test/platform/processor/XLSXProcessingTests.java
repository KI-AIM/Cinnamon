package de.kiaim.test.platform.processor;


import de.kiaim.model.configuration.data.ColumnConfiguration;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.model.configuration.data.DateFormatConfiguration;
import de.kiaim.model.data.*;
import de.kiaim.model.enumeration.DataScale;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.platform.PlatformApplication;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.entity.FileConfigurationEntity;
import de.kiaim.platform.model.enumeration.DatatypeEstimationAlgorithm;
import de.kiaim.platform.model.file.FileType;
import de.kiaim.platform.processor.XlsxProcessor;
import de.kiaim.test.util.FileConfigurationTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.time.LocalDate;
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
