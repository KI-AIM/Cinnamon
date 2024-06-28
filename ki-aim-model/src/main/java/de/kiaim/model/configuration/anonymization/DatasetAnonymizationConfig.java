package de.kiaim.model.configuration.anonymization;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

/**
 * KI-AIM full AnonymizationConfig.
 */
@Schema(description = "Complete anonymization configuration for a dataset.")
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class DatasetAnonymizationConfig {
//    Take in KI AIM specific data configuration or anon configuration etc
//    And save it as KI-AIM DatasetAnonymizationConfig object
//    Add default information
    private AnonymizationConfig anonymizationConfiguration;
    private List<AttributeConfig> attributeConfigurations;

}
