package de.kiaim.anon.processor;

import de.kiaim.anon.AbstractAnonymizationTests;
import de.kiaim.model.data.DataSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static de.kiaim.anon.service.CompatibilityAssurance.isDataSetCompatible;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class AnonymizedDatasetProcessorTest extends AbstractAnonymizationTests {

    @Autowired
    private AnonymizedDatasetProcessor anonymizedDatasetProcessor;

    private DataSet anonymizedDataset;

    @BeforeEach
    public void setAnonymizedDataset() throws Exception {
        anonymizedDataset = anonymizationService.anonymizeData(dataSet, kiaimAnonConfig);
    }

    @Test
    public void testConvertToDataSet_ValidData() throws Exception {
        setAnonymizedDataset();
//        assertNotNull(anonymizedDataset);
//
//        System.out.println("result");
//        System.out.println(anonymizedDataset.toString().substring(0,3000));
//        assert isDataSetCompatible(anonymizedDataset);
    }

}
