package de.kiaim.model.configuration.anonymization;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

/**
 * KI-AIM AnonymizationConfig.
 */
@Schema(description = "Anonymization configuration for a dataset.")
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class AnonymizationConfig {

    private List<PrivacyModel> privacyModels;
    private String suppressionLimit;
    private String qualityModel;
    private boolean localGeneralization;
    private List<AttributeConfig> attributeConfigurations;
}
