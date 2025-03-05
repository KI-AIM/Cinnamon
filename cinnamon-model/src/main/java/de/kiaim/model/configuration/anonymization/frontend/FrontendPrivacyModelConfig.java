package de.kiaim.model.configuration.anonymization.frontend;

import de.kiaim.model.exception.anonymization.InvalidAttributeConfigException;
import de.kiaim.model.exception.anonymization.InvalidGeneralizationSettingException;
import de.kiaim.model.exception.anonymization.InvalidRiskThresholdException;
import de.kiaim.model.exception.anonymization.InvalidSuppressionLimitException;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
public class FrontendPrivacyModelConfig {
    private String name;
    private String type;
    private FrontendModelConfig modelConfiguration;

}
