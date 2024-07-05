package de.kiaim.anon.service;

import de.kiaim.anon.AbstractAnonymizationTests;
import de.kiaim.anon.config.AnonymizationConfig;
import de.kiaim.anon.converter.KiaimAnonConfigConverter;
import de.kiaim.model.data.DataSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import smile.data.Dataset;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class AnonymizationServiceTest extends AbstractAnonymizationTests {

    @Autowired
    private AnonymizationService anonymizationService;

    @Autowired
    private KiaimAnonConfigConverter datasetAnonConfigConverter;

    @Test
    public void testAnonymizationService() throws Exception {
        AnonymizationConfig anonymizationConfigConverted = datasetAnonConfigConverter.convert(kiaimAnonConfig, dataSet.getDataConfiguration());
        System.out.println("AnonConfig converted in JAL object:");
        System.out.println(anonymizationConfigConverted);

        DataSet anonymizedDataset = anonymizationService.anonymizeData(dataSet, kiaimAnonConfig);
        assertNotNull(anonymizedDataset);
    }

}
