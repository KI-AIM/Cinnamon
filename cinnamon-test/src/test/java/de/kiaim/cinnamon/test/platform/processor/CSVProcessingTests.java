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
import de.kiaim.cinnamon.platform.processor.CsvProcessor;
import de.kiaim.cinnamon.test.util.FileConfigurationTestHelper;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = PlatformApplication.class)
@ActiveProfiles("test")
public class CSVProcessingTests {

    @Autowired
    CsvProcessor csvProcessor;


    @Test
    void testReadMethodOfCsvProcessor() {
        String csvData =
                """
               650390,Tonisha Swift,1975-05-08,no,303.23 €
               208589,Wilson Maggio,1994-02-28,no,23623.18 €
               452159,Bill Hintz,1987-05-17,no,38.41 €
               730160,"Heathcote, Nelia",1959-02-03,yes,21.01 €
               614164,Ms. Chester Keebler,1982-02-20,no,158.79 €
                """.trim();
        FileConfigurationEntity fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(FileType.CSV, false);

        DataConfiguration config = getDataConfiguration();

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        TransformationResult actualResult = csvProcessor.read(stream, fileConfiguration, config);

        TransformationResult expectedResult = testReadMethodOfCsvProcessor_getExpectedTransformationResult();

        assertEquals(actualResult, expectedResult);
    }

    @Test
    void testReadMethodOfCsvProcessorWithHeader() {
        String csvData =
                """
               id,name,birthdate,smoker,price
               650390,Tonisha Swift,1975-05-08,no,303.23 €
               208589,Wilson Maggio,1994-02-28,no,23623.18 €
               452159,Bill Hintz,1987-05-17,no,38.41 €
               730160,"Heathcote, Nelia",1959-02-03,yes,21.01 €
               614164,Ms. Chester Keebler,1982-02-20,no,158.79 €
                """.trim();
        FileConfigurationEntity fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(FileType.CSV, true);

        DataConfiguration config = getDataConfiguration();

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        TransformationResult actualResult = csvProcessor.read(stream, fileConfiguration, config);

        TransformationResult expectedResult = testReadMethodOfCsvProcessor_getExpectedTransformationResult();

        assertEquals(actualResult, expectedResult);
    }


    private TransformationResult testReadMethodOfCsvProcessor_getExpectedTransformationResult() {
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
    void testEstimationMostEstimated() {
        String csvData =
                """
               650390,Tonisha Swift,1975-05-08,no,303.23 €
               208589,Wilson Maggio,1994-02-28,no,23623.18 €
               string,Bill Hintz,1987-05-17,no,38.41 €
               730160,Nelia Heathcote,1959-02-03,yes,21.01 €
               614164,Ms. Chester Keebler,1982-02-20,no,158.79 €
                """.trim();
        FileConfigurationEntity fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(FileType.CSV, false);


        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        DataConfiguration actualConfiguration = csvProcessor.estimateDataConfiguration(stream, fileConfiguration,
                                                                                       DatatypeEstimationAlgorithm.MOST_ESTIMATED);

        DataConfiguration expectedConfiguration = removeNames(getDataConfiguration());
        assertEquals(expectedConfiguration, actualConfiguration);
    }

    @Test
    void testEstimationMostGeneral() {
        String csvData =
                """
               650390,Tonisha Swift,1975-05-08,no,303.23 €
               208589,Wilson Maggio,1994-02-28,no,23623.18 €
               string,Bill Hintz,1987-05-17,no,38.41 €
               730160,Nelia Heathcote,1959-02-03,yes,21.01 €
               614164,Ms. Chester Keebler,1982-02-20,no,158.79 €
                """.trim();
        FileConfigurationEntity fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(FileType.CSV, false);


        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        DataConfiguration actualConfiguration = csvProcessor.estimateDataConfiguration(stream, fileConfiguration,
                                                                                       DatatypeEstimationAlgorithm.MOST_GENERAL);

        DataConfiguration expectedConfiguration = removeNames(getDataConfiguration());
        expectedConfiguration.getConfigurations().get(0).setType(DataType.STRING);
        expectedConfiguration.getConfigurations().get(0).setScale(DataScale.NOMINAL);
        assertEquals(expectedConfiguration, actualConfiguration);
    }

    @Test
    void testEstimationHeaders() {
        String csvData =
                """
               id,name,birthdate,smoker,price
               650390,Tonisha Swift,1975-05-08,no,303.23 €
               208589,Wilson Maggio,1994-02-28,no,23623.18 €
               452159,Bill Hintz,1987-05-17,no,38.41 €
               730160,Nelia Heathcote,1959-02-03,yes,21.01 €
               614164,Ms. Chester Keebler,1982-02-20,no,158.79 €
                """.trim();
        FileConfigurationEntity fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(FileType.CSV, true);

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        DataConfiguration actualConfiguration = csvProcessor.estimateDataConfiguration(stream, fileConfiguration,
                                                                                       DatatypeEstimationAlgorithm.MOST_ESTIMATED);

        DataConfiguration expectedConfiguration = getDataConfiguration();

        assertEquals(expectedConfiguration, actualConfiguration);
    }

    @Test
    void testEstimationSparse() {
        String csvData = """
                id,name,birthdate,smoker,price
                650390,,1975-05-08,no,303.23 €
                208589,,1994-02-28,,23623.18 €
                452159,,1987-05-17,,38.41 €
                730160,,1959-02-03,yes,21.01 €
                730160,,1959-02-03,yes,21.01 €
                730160,,1959-02-03,yes,21.01 €
                730160,,1959-02-03,yes,21.01 €
                730160,,1959-02-03,yes,21.01 €
                730160,,1959-02-03,yes,21.01 €
                730160,,1959-02-03,yes,21.01 €
                614164,,1982-02-20,,158.79 €""";
        FileConfigurationEntity fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(FileType.CSV, true);

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        DataConfiguration actualConfiguration = csvProcessor.estimateDataConfiguration(stream, fileConfiguration,
                                                                                       DatatypeEstimationAlgorithm.MOST_ESTIMATED);

        DataConfiguration expectedConfiguration = getDataConfiguration();
        expectedConfiguration.getConfigurations().get(1).setType(DataType.UNDEFINED);

        assertEquals(expectedConfiguration, actualConfiguration);
    }

    @Test
    void testEstimationAdditionalColumn() {
        String csvData = """
               id,name,birthdate,smoker,price
               650390,Tonisha Swift,1975-05-08,no,303.23 €,
               208589,Wilson Maggio,1994-02-28,no,23623.18 €
               452159,Bill Hintz,1987-05-17,no,38.41 €,
               730160,Nelia Heathcote,1959-02-03,yes,21.01 €
               614164,Ms. Chester Keebler,1982-02-20,no,158.79 €""";
        FileConfigurationEntity fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration(FileType.CSV, true);

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        DataConfiguration actualConfiguration = csvProcessor.estimateDataConfiguration(stream, fileConfiguration,
                                                                                       DatatypeEstimationAlgorithm.MOST_ESTIMATED);

        DataConfiguration expectedConfiguration = getDataConfiguration();
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

    private static DataConfiguration removeNames(final DataConfiguration config) {
        for (var col : config.getConfigurations()) {
            col.setName("");
        }
        return config;
    }
}
