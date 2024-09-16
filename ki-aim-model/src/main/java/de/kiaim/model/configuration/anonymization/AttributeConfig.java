package de.kiaim.model.configuration.anonymization;

import de.kiaim.model.enumeration.DataScale;
import de.kiaim.model.enumeration.DataType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

/**
 * KI-AIM AttributeConfig for DatasetAnonymizationConfig.
 */
@Schema(description = "Attribute configuration for an Anonymization Configuration.")
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class AttributeConfig {
    private final int index;
    private final String attributeType;
    private final List<String> order;
    private final String transformation;
    private final String anonyGroup;
    private final Hierarchy hierarchy;
}
