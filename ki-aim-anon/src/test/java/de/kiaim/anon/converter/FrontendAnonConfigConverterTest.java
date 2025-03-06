package de.kiaim.anon.converter;

import de.kiaim.anon.AbstractAnonymizationTests;
import de.kiaim.anon.config.AnonymizationConfig;
import de.kiaim.model.configuration.anonymization.frontend.FrontendAttributeConfig;
import de.kiaim.model.exception.anonymization.InvalidAttributeConfigException;
import de.kiaim.model.exception.anonymization.InvalidGeneralizationSettingException;
import de.kiaim.model.exception.anonymization.InvalidRiskThresholdException;
import de.kiaim.model.exception.anonymization.InvalidSuppressionLimitException;
import org.bihmi.jal.config.AttributeConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class FrontendAnonConfigConverterTest extends AbstractAnonymizationTests {

    @Test
    public void testConvertFrontendConfigToAnonymizationConfig() throws InvalidSuppressionLimitException, InvalidRiskThresholdException, InvalidAttributeConfigException, InvalidGeneralizationSettingException {
        // Convert frontend config in JAL anon config
        AnonymizationConfig anonymizationConfig = FrontendAnonConfigConverter.convertToJALConfig(frontendAnonConfig.getAnonymization(), dataSet);

//        System.out.println("Anonymization Config");
//        System.out.println(anonymizationConfig);

//         Check conversion
        assertNotNull(anonymizationConfig);
        assertEquals(frontendAnonConfig.getAnonymization().getPrivacyModels().get(0).getModelConfiguration().getSuppressionLimit(), String.valueOf(anonymizationConfig.getSuppressionLimit()));

        // Check if all attributes are converted
        assertEquals(frontendAnonConfig.getAnonymization().getAttributeConfiguration().size(), anonymizationConfig.getAttributeConfigs().size());

        for (int i = 0; i < frontendAnonConfig.getAnonymization().getAttributeConfiguration().size(); i++) {
            FrontendAttributeConfig frontendAttribute = frontendAnonConfig.getAnonymization().getAttributeConfiguration().get(i);
            AttributeConfig attributeConfig = anonymizationConfig.getAttributeConfigs().get(i);

            assertEquals(frontendAttribute.getName(), attributeConfig.getName());
            assertEquals(frontendAttribute.getDataType().toString(), attributeConfig.getDataType());
            assertEquals(frontendAttribute.getDateFormat(), attributeConfig.getDateFormat());
        }

        // Check privacy model conversion
        assertNotNull(anonymizationConfig.getPrivacyModelList());
        assertFalse(anonymizationConfig.getPrivacyModelList().isEmpty());

    }

}
