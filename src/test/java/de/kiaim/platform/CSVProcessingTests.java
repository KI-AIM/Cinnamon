package de.kiaim.platform;

import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.data.DataType;
import de.kiaim.platform.model.data.configuration.ColumnConfiguration;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
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
import java.util.ArrayList;
import java.util.List;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = PlatformApplication.class)
@ActiveProfiles("test")
public class CSVProcessingTests {

    @Autowired
    CsvProcessor csvProcessor;


    @Test
    void testTransformation() {
        String csvData =
                """
               650390,Tonisha Swift,1975-05-08,no,303.23 €
               208589,Wilson Maggio,1994-02-28,no,23623.18 €
               452159,Bill Hintz,1987-05-17,no,38.41 €
               730160,Nelia Heathcote,1959-02-03,yes,21.01 €
               614164,Ms. Chester Keebler,1982-02-20,no,158.79 €
                """.trim();

        DataConfiguration config = getDataConfiguration();

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        TransformationResult readResult = csvProcessor.read(stream, config);

        assert readResult.getTransformationErrors().isEmpty();
        assert readResult.getDataSet().getDataRows().size() == 5;
    }


    @Test
    void testEstimation() {
        String csvData =
                """
               650390,Tonisha Swift,1975-05-08,no,303.23 €
               208589,Wilson Maggio,1994-02-28,no,23623.18 €
               452159,Bill Hintz,1987-05-17,no,38.41 €
               730160,Nelia Heathcote,1959-02-03,yes,21.01 €
               614164,Ms. Chester Keebler,1982-02-20,no,158.79 €
                """.trim();


        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        DataConfiguration config = csvProcessor.estimateDatatypes(stream);

        assert config.getConfigurations().get(0).getType().equals(getDataConfiguration().getConfigurations().get(0).getType()); //INTEGER
        assert config.getConfigurations().get(1).getType().equals(getDataConfiguration().getConfigurations().get(1).getType()); //STRING
        assert config.getConfigurations().get(2).getType().equals(getDataConfiguration().getConfigurations().get(2).getType()); //DATE
        assert config.getConfigurations().get(3).getType().equals(getDataConfiguration().getConfigurations().get(3).getType()); //BOOLEAN
        assert config.getConfigurations().get(4).getType().equals(getDataConfiguration().getConfigurations().get(4).getType()); //STRING
    }


    private static DataConfiguration getDataConfiguration() {
        DataConfiguration config = new DataConfiguration();

        ColumnConfiguration column1 = new ColumnConfiguration(
                0, "id", DataType.INTEGER, new ArrayList<>()
        );

        ColumnConfiguration column2 = new ColumnConfiguration(
                1, "name", DataType.STRING, new ArrayList<>()
        );

        ColumnConfiguration column3 = new ColumnConfiguration(
                2, "birthdate", DataType.DATE, new ArrayList<>()
        );

        ColumnConfiguration column4 = new ColumnConfiguration(
                3, "smoker", DataType.BOOLEAN, new ArrayList<>()
        );

        ColumnConfiguration column5 = new ColumnConfiguration(
                4, "price", DataType.STRING, new ArrayList<>()
        );

        config.setConfigurations(List.of(column1, column2, column3, column4, column5));
        return config;
    }
}
