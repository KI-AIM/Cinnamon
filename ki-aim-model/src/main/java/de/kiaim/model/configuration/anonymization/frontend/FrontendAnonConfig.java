package de.kiaim.model.configuration.anonymization.frontend;

import de.kiaim.model.enumeration.DataScale;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.model.enumeration.anonymization.AttributeProtection;
import de.kiaim.model.exception.anonymization.InvalidAttributeConfigException;
import de.kiaim.model.exception.anonymization.InvalidGeneralizationSettingException;
import de.kiaim.model.exception.anonymization.InvalidRiskThresholdException;
import de.kiaim.model.exception.anonymization.InvalidSuppressionLimitException;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * Class to represent the anonymization configuration received from the frontend.
 * In combination with the data configuration, this object will be used to generate a JAL AnonymizationConfiguration object.
 */
@Getter
@Setter
@ToString
public class FrontendAnonConfig {
    private List<FrontendPrivacyModelConfig> privacyModels;
    private List<FrontendAttributeConfig> attributeConfigurations;

    // Validate attribute configurations based on DataType, AttributeProtection, and DataScale
    public void validateAttributeConfigurations() throws InvalidAttributeConfigException {
        for (FrontendAttributeConfig attribute : attributeConfigurations) {
            // Each attribute is responsible for its own validation
            attribute.validate();
        }
    }

    // Call this method to validate the whole config
    public void validateConfig() throws InvalidRiskThresholdException, InvalidSuppressionLimitException, InvalidGeneralizationSettingException, InvalidAttributeConfigException {
        validateAttributeConfigurations();
        privacyModels.get(0).getModelConfiguration().validate();
    }
}
