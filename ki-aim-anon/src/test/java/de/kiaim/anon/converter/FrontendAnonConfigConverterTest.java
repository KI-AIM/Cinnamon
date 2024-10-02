package de.kiaim.anon.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.kiaim.anon.AbstractAnonymizationTests;
import de.kiaim.anon.config.AnonymizationConfig;
import de.kiaim.model.configuration.anonymization.frontend.FrontendAnonConfig;
import de.kiaim.model.configuration.anonymization.frontend.FrontendAnonConfigReader;
import de.kiaim.model.configuration.anonymization.frontend.FrontendAttributeConfig;
import de.kiaim.model.data.DataSet;
import de.kiaim.model.exception.anonymization.InvalidAttributeConfigException;
import de.kiaim.model.exception.anonymization.InvalidGeneralizationSettingException;
import de.kiaim.model.exception.anonymization.InvalidRiskThresholdException;
import de.kiaim.model.exception.anonymization.InvalidSuppressionLimitException;
import org.bihmi.jal.config.AttributeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class FrontendAnonConfigConverterTest extends AbstractAnonymizationTests {

    @Test
    public void testConvertFrontendConfigToAnonymizationConfig() throws InvalidSuppressionLimitException, InvalidRiskThresholdException, InvalidAttributeConfigException, InvalidGeneralizationSettingException {
        // Convert frontend config in JAL anon config
        AnonymizationConfig anonymizationConfig = FrontendAnonConfigConverter.convertToJALConfig(frontendAnonConfig, dataSet);

        System.out.println("Anonymization Config");
        System.out.println(anonymizationConfig);

//         Check convertion
        assertNotNull(anonymizationConfig);
        assertEquals(frontendAnonConfig.getSuppressionLimit(), String.valueOf(anonymizationConfig.getSuppressionLimit()));

        // Check if all attributes are converted
        assertEquals(frontendAnonConfig.getAttributeConfigurations().size(), anonymizationConfig.getAttributeConfigs().size());

        for (int i = 0; i < frontendAnonConfig.getAttributeConfigurations().size(); i++) {
            FrontendAttributeConfig frontendAttribute = frontendAnonConfig.getAttributeConfigurations().get(i);
            AttributeConfig attributeConfig = anonymizationConfig.getAttributeConfigs().get(i);

            assertEquals(frontendAttribute.getName(), attributeConfig.getName());
            assertEquals(frontendAttribute.getDataType().toString(), attributeConfig.getDataType());
            assertEquals(frontendAttribute.getDateFormat(), attributeConfig.getDateFormat());
        }

        // Vérifier la conversion de PrivacyModel
        assertNotNull(anonymizationConfig.getPrivacyModelList());
        assertFalse(anonymizationConfig.getPrivacyModelList().isEmpty());

    }

}
