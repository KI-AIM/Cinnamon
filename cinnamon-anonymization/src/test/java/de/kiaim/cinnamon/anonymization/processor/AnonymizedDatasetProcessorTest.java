package de.kiaim.cinnamon.anonymization.processor;

import de.kiaim.cinnamon.anonymization.AbstractAnonymizationTests;
import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.model.data.Data;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.model.data.DecimalData;
import de.kiaim.cinnamon.model.data.TextData;
import de.kiaim.cinnamon.model.enumeration.DataScale;
import de.kiaim.cinnamon.model.enumeration.DataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static de.kiaim.cinnamon.anonymization.processor.AnonymizedDatasetProcessor.containsStarWithOtherCharacters;
import static de.kiaim.cinnamon.anonymization.service.CompatibilityAssurance.isDataSetCompatible;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AnonymizedDatasetProcessorTest extends AbstractAnonymizationTests {

    @Autowired
    private AnonymizedDatasetProcessor anonymizedDatasetProcessor;

    private DataSet anonymizedDataset;

    @BeforeEach
    public void setAnonymizedDataset() throws Exception {
        String anonymizedDatasetPath = "data/oncology/anonymized-dataset-demo-data_DE 25k-anon-config-v1.json";
        anonymizedDataset = importAnonymizedDataset(anonymizedDatasetPath);
    }

    public DataSet importAnonymizedDataset(String anonymizedDatasetPath) throws IOException {
        String dataSetJson = new String(Files.readAllBytes(Paths.get(anonymizedDatasetPath)));
        return jsonMapper.readValue(dataSetJson, DataSet.class);
    }

    @Test
    public void testConvertToDataSet_ValidData() throws Exception {
        setAnonymizedDataset();
        assertNotNull(anonymizedDataset);

//        System.out.println("result");
//        System.out.println(anonymizedDataset.toString().substring(0,3000));
        assert isDataSetCompatible(anonymizedDataset);
    }

    @Test
    public void testContainsStarWithOtherCharacters() throws Exception {
        String value1 = "0.6";
        assertFalse(containsStarWithOtherCharacters(value1)); // Should return false

        // Test: Value with only '*' characters
        String value2 = "****";
        assertFalse(containsStarWithOtherCharacters(value2)); // Should return false

        // Test: Value with '*' and other characters (valid case)
        String value3 = "4,0***";
        assertTrue(containsStarWithOtherCharacters(value3)); // Should return true

        // Test: Value with '*' and a single character (valid case)
        String value4 = "A**";
        assertTrue(containsStarWithOtherCharacters(value4)); // Should return true

        // Test: Value with multiple characters and a '*' (valid case)
        String value5 = "Hello*world";
        assertTrue(containsStarWithOtherCharacters(value5)); // Should return true

        // Test: Value with '*' and multiple digits (valid case)
        String value6 = "123*456";
        assertTrue(containsStarWithOtherCharacters(value6));

    }

    private static Stream<Arguments> conversionTestCases() {
        String[][] nullSample = {{"note"}, {"null"}};
        String[][] textSample = {{"note"}, {"Patient reports mild symptoms after treatment."}};
        String[][] decimalSample = {{"weight"}, {"0.3"}};
        return Stream.of(
                Arguments.of(nullSample, DataType.TEXT, DataScale.NOMINAL, TextData.class, null),
                Arguments.of(textSample, DataType.TEXT, DataScale.NOMINAL, TextData.class, textSample[1][0]),
                Arguments.of(decimalSample, DataType.DECIMAL, DataScale.ORDINAL, DecimalData.class,
                        Float.parseFloat(decimalSample[1][0]))
        );
    }

    @ParameterizedTest
    @MethodSource("conversionTestCases")
    public void testConvertToDataSet(String[][] anonymizedData, DataType dataType,
                                     DataScale dataScale, Class<?> cls, Object expected) {
        DataConfiguration config = dataConfiguration(anonymizedData[0][0], dataType, dataScale);
        DataSet result = AnonymizedDatasetProcessor.convertToDataSet(anonymizedData, config);

        assertEquals(1, result.getDataRows().size());
        Data data = result.getDataRows().get(0).getData().get(0);
        assertInstanceOf(cls, data);
        assertEquals(expected, data.getValue());
        assertTrue(isDataSetCompatible(result));
    }

    private static DataConfiguration dataConfiguration(String name, DataType dataType,
                                                           DataScale dataScale) {
        DataConfiguration configuration = new DataConfiguration();
        ColumnConfiguration column = new ColumnConfiguration();
        column.setIndex(0);
        column.setName(name);
        column.setType(dataType);
        column.setScale(dataScale);
        configuration.addColumnConfiguration(column);
        return configuration;
    }

}
