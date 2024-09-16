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

    private final List<PrivacyModel> privacyModels;
    private final String suppressionLimit;
    private final String qualityModel;
    private final boolean localGeneralization;
    private final List<AttributeConfig> attributeConfigurations;
}
