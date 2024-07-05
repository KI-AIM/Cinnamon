package de.kiaim.anon.service;

import de.kiaim.anon.AbstractAnonymizationTests;
import de.kiaim.anon.config.AnonymizationConfig;
import de.kiaim.anon.converter.KiaimAnonConfigConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class AnonymizationServiceTest extends AbstractAnonymizationTests {

    @Autowired
    private AnonymizationService anonymizationService;

    @Autowired
    private KiaimAnonConfigConverter datasetAnonConfigConverter;

    @Test
    public void testAnonymizationService(){
        AnonymizationConfig anonymizationConfigConverted = datasetAnonConfigConverter.convert(kiaimAnonConfig, dataSet.getDataConfiguration());
        System.out.println("AnonConfig converted in JAL object:");
        System.out.println(anonymizationConfigConverted);
    }

}
