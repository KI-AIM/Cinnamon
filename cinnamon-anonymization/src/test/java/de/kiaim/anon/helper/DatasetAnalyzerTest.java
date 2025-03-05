package de.kiaim.anon.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.anon.AbstractAnonymizationTests;
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
public class DatasetAnalyzerTest extends AbstractAnonymizationTests {

    @Test
    public void testFindMinMax() throws Exception {
//        System.out.println(dataSet.getDataConfiguration().getDataTypes().toString());
        Number[] minMax = DatasetAnalyzer.findMinMaxForColumn(dataSet, 13);

        assertEquals(210, minMax[0]);
        assertEquals(245, minMax[1]);
    }
}
