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
    private int index;
    private String attributeType;
    private List<String> order;
    private String transformation;
    private String anonyGroup;
    private Hierarchy hierarchy;
}
