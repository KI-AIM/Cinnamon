package de.kiaim.anon.converter;

import de.kiaim.model.configuration.anonymization.Hierarchy;
import org.bihmi.jal.config.HierarchyConfig;

public class HierarchyConverter {

    public static HierarchyConfig convert(Hierarchy kiHierarchy,
                                          String attributeName){
//        TODO : add levels to AnonConfig
//        TODO : mergeLevels ??
        if (kiHierarchy == null) {
            return null;
        }
        return HierarchyConfig.builder()
                .attributeName(attributeName)
                .hierarchyType(kiHierarchy.getType())
                .intervalSize(kiHierarchy.getIntervalSize())
                .splitLevels(kiHierarchy.getSplitLevels())
                .minLevelToUse(kiHierarchy.getMinLevel())
                .maxLevelToUse(kiHierarchy.getMaxLevel())
                .build();
    }
}
