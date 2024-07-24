package de.kiaim.anon.service;

import de.kiaim.anon.AbstractAnonymizationTests;
import de.kiaim.anon.config.AnonymizationConfig;
import de.kiaim.anon.converter.KiaimAnonConfigConverter;
import de.kiaim.model.configuration.data.ColumnConfiguration;
import de.kiaim.model.data.DataSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import smile.data.Dataset;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class AnonymizationServiceTest extends AbstractAnonymizationTests {

    @Autowired
    private AnonymizationService anonymizationService;

    @Autowired
    private KiaimAnonConfigConverter datasetAnonConfigConverter;

    @Test
    public void testAnonymizationService() throws Exception {

        // Lancement du processus d'anonymisation en mode asynchrone
        Future<DataSet> future = anonymizationService.anonymizeData(dataSet, kiaimAnonConfig);

        if (!future.isDone()) {
            for (int i = 0; i<30; i++) {
                Thread.sleep(100);
            }
        }

        try {
            DataSet anonymizedDataset = future.get();
            assertNotNull(anonymizedDataset);
            System.out.println(anonymizedDataset.getDataRows());
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            throw e;
        }
    }

}
