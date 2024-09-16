package org.bihmi.jal.config;

import lombok.*;
import org.deidentifier.arx.AttributeType.Hierarchy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hierarchy Config to specify generalization hierarchies
 *
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HierarchyConfig {
    private String attributeName;
    private String hierarchyType;
    private String intervalSize;
    private String splitLevels; // splits top level further down, opposite of merge levels
    // private String mergeLevels; // merge bottom levels together, opposite of split levels

    // TODO min and max levels are typically set in the attribute configuration and is not part of the hierarchy creation
    private Integer minLevelToUse; // min level from hierarchy to use
    private Integer maxLevelToUse; // max level from hierarchy to use
    private Map<String, Object> levels; // TODO: using Map<String, Object> is very uncomfortable for further usage. Maybe new data type needed (looks like a tree)

        @Override
    public String toString() {
        return "HierarchyConfig {" +
                "hierarchyType='" + hierarchyType + '\'' +
                ", attributeName='" + attributeName + '\'' +
                ", intervalSize='" + intervalSize + '\'' +
                ", splitLevels='" + splitLevels + '\'' +
                // ", mergeLevels='" + mergeLevels + '\'' +
                ", minLevelToUse=" + minLevelToUse +
                ", maxLevelToUse=" + maxLevelToUse +
                '}';
    }
}

