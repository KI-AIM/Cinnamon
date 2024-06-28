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
    private String type;
    private String intervalSize;
    private String splitLevels;
    private int minLevel;
    private int maxLevel;
}
