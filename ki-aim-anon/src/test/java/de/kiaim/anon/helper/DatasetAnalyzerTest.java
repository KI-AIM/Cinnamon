package de.kiaim.anon.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.data.DataSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class DatasetAnalyzerTest {
    @Autowired
    protected ObjectMapper objectMapper;

    protected DataSet dataSet;

    @BeforeEach
    public void setUp() throws Exception {
        String datasetPath = "data/data.json-dataset-demo-data_DE 25k.json";

        dataSet = importDataset(datasetPath);

    }

    public DataSet importDataset(String datasetPath) throws IOException {
        String dataSetJson = new String(Files.readAllBytes(Paths.get("data/data.json-dataset-demo-data_DE 25k.json")));
        return objectMapper.readValue(dataSetJson, DataSet.class);
    }

    @Test
    public void testProcessAnonymization() throws Exception {
//        System.out.println(dataSet.getDataConfiguration().getDataTypes().toString());
        Number[] minMax = DatasetAnalyzer.findMinMaxForColumn(dataSet, 13);

        assertEquals(210, minMax[0]);
        assertEquals(245, minMax[1]);
    }
}
