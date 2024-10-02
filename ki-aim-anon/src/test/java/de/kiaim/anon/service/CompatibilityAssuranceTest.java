package de.kiaim.anon.service;

import de.kiaim.anon.AbstractAnonymizationTests;
import de.kiaim.anon.exception.AttributeMismatchException;
import de.kiaim.anon.exception.ColumnNumberMismatchException;
import de.kiaim.model.configuration.anonymization.frontend.FrontendAnonConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class CompatibilityAssuranceTest extends AbstractAnonymizationTests {

    public FrontendAnonConfig importIncompatibleFrontendAnonConfig(String frontendAnonConfigPath) throws IOException {
        File file = ResourceUtils.getFile(frontendAnonConfigPath);
        return frontendAnonConfigReader.readFrontendAnonConfig(file.getAbsolutePath());
    }

    @Test
    public void testValidCompatibility() {
        assertDoesNotThrow(() -> CompatibilityAssurance.checkDataSetAndFrontendConfigCompatibility(dataSet, frontendAnonConfig));
    }

//    @Test
//    public void testColumnNumberMismatch() throws IOException {
//        String frontendAnonConfigPath = "data/data.invalid-dataset-config-13-attributes.yml";
//
//        FrontendAnonConfig shorterFrontendConfig = importIncompatibleFrontendAnonConfig(frontendAnonConfigPath);
//
//        assertThrows(ColumnNumberMismatchException.class, () -> CompatibilityAssurance.checkDataSetAndFrontendConfigCompatibility(dataSet, shorterFrontendConfig));
//    }
//
//    @Test
//    public void testAttributeNameMismatch() throws IOException {
//        String anonConfigPath = "data/data.invalid-dataset-config-attr53.yml";
//        FrontendAnonConfig wrongAttrConfigFrontendConfig = importIncompatibleFrontendAnonConfig(anonConfigPath);
//
//        assertThrows(AttributeMismatchException.class, () -> CompatibilityAssurance.checkDataSetAndFrontendConfigCompatibility(dataSet, wrongAttrConfigFrontendConfig));
//    }
//
//    @Test
//    public void testDataTypeMismatch() throws IOException {
//        String anonConfigPath = "data/data.invalid-dataset-config-attr53.yml";
//        FrontendAnonConfig wrongAttrConfigFrontendConfig = importIncompatibleFrontendAnonConfig(anonConfigPath);
//
//        assertThrows(AttributeMismatchException.class, () -> CompatibilityAssurance.checkDataSetAndFrontendConfigCompatibility(dataSet, wrongAttrConfigFrontendConfig));
//    }
//
//    @Test
//    public void testDataScaleMismatch() throws IOException {
//        String anonConfigPath = "data/data.invalid-dataset-config-attr53.yml";
//        FrontendAnonConfig wrongAttrConfigFrontendConfig = importIncompatibleFrontendAnonConfig(anonConfigPath);
//
//        assertThrows(AttributeMismatchException.class, () -> CompatibilityAssurance.checkDataSetAndFrontendConfigCompatibility(dataSet, wrongAttrConfigFrontendConfig));
//    }
}
