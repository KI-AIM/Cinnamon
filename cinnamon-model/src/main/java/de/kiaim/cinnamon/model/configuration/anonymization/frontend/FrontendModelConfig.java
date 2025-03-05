package de.kiaim.cinnamon.model.configuration.anonymization.frontend;

import de.kiaim.cinnamon.model.exception.anonymization.InvalidAttributeConfigException;
import de.kiaim.cinnamon.model.exception.anonymization.InvalidGeneralizationSettingException;
import de.kiaim.cinnamon.model.exception.anonymization.InvalidRiskThresholdException;
import de.kiaim.cinnamon.model.exception.anonymization.InvalidSuppressionLimitException;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
public class FrontendModelConfig {
    private String riskThresholdType; // "Max" or "Avg"
    private float riskThresholdValue; // if: riskThresholdType == Max : [0.05, 0.075, 0.1, 0.2, 0.5]
    // if: riskThresholdType == Avg : [0.0005, 0.001, 0.005, 0.05, 0.075, 0.1, 0.2, 0.5]
    private String suppressionLimit; // [0,100]
    private String generalizationSetting; // "Global" or "Local"

    // Validate the attribute configuration based on its type, scale, and protection
    public void validate() throws
            InvalidAttributeConfigException,
            InvalidGeneralizationSettingException,
            InvalidSuppressionLimitException,
            InvalidRiskThresholdException {
        validateRiskThreshold();
        validateSuppressionLimit();
        validateGeneralizationSetting();
    }

    // Validate that the risk threshold value matches the type (Max or Avg)
    public void validateRiskThreshold() throws InvalidRiskThresholdException {
        if (riskThresholdType.equals("Max")) {
            List<Float> allowedValues = List.of(0.05f, 0.075f, 0.1f, 0.2f, 0.5f);
            if (!allowedValues.contains(riskThresholdValue)) {
                throw new InvalidRiskThresholdException("Invalid riskThresholdValue for 'Max'. Allowed values: " + allowedValues);
            }
        } else if (riskThresholdType.equals("Avg")) {
            List<Float> allowedValues = List.of(0.0005f, 0.001f, 0.005f, 0.05f, 0.075f, 0.1f, 0.2f, 0.5f);
            if (!allowedValues.contains(riskThresholdValue)) {
                throw new InvalidRiskThresholdException("Invalid riskThresholdValue for 'Avg'. Allowed values: " + allowedValues);
            }
        } else {
            throw new InvalidRiskThresholdException("Unknown riskThresholdType. Must be 'Max' or 'Avg'.");
        }
    }

    // Validate the suppression limit is between 0 and 1
    public void validateSuppressionLimit() throws InvalidSuppressionLimitException {
        float suppressionValue = Float.parseFloat(suppressionLimit);
        if (suppressionValue < 0 || suppressionValue > 100) {
            throw new InvalidSuppressionLimitException("Suppression limit must be between 0 and 100.");
        }
    }

    // Validate the generalization setting is either Global or Local
    public void validateGeneralizationSetting() throws InvalidGeneralizationSettingException {
        if (!generalizationSetting.equals("Global") && !generalizationSetting.equals("Local")) {
            throw new InvalidGeneralizationSettingException("Generalization setting must be either 'Global' or 'Local'.");
        }
    }
}
