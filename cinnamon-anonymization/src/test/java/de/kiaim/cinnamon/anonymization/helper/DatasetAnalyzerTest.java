package de.kiaim.cinnamon.anonymization.helper;

import de.kiaim.cinnamon.anonymization.AbstractAnonymizationTests;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

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
