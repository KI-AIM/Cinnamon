package de.kiaim.model.configuration.anonymization;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(description = "Hierarchy for a specific dataset attribute for the anonymization configuration.")
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class Hierarchy {
    private final String type;
    private final String intervalSize;
    private final String splitLevels;
    private final int minLevel;
    private final int maxLevel;
}
