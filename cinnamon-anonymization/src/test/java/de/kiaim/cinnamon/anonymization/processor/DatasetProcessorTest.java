package de.kiaim.cinnamon.anonymization.processor;

import de.kiaim.cinnamon.anonymization.AbstractAnonymizationTests;
import de.kiaim.cinnamon.model.data.DataSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static de.kiaim.cinnamon.anonymization.service.CompatibilityAssurance.isDataSetCompatible;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DatasetProcessorTest extends AbstractAnonymizationTests {

    @Autowired
    private DataSetProcessor processor;

    private DataSet dataset;

    @BeforeEach
    public void setDataset() throws Exception {
        String anonymizedDatasetPath = "data/oncology/data.json-dataset-demo-data_DE 25k.json";
        dataset = importDataset(anonymizedDatasetPath);
    }

    public DataSet importDataset(String anonymizedDatasetPath) throws IOException {
        String dataSetJson = new String(Files.readAllBytes(Paths.get(anonymizedDatasetPath)));
        return objectMapper.readValue(dataSetJson, DataSet.class);
    }

    // convertDatasetToStringArray


    @Test
    public void testConvertToDataSet_ValidData() throws Exception {
        setDataset();
        assertNotNull(dataset);
        processor = new DataSetProcessor();
        processor.convertDatasetToStringArray(dataset);
        // assert isDataSetCompatible(dataset);
    }

}
