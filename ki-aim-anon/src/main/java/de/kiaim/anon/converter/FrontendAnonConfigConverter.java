package de.kiaim.anon.converter;

import de.kiaim.anon.helper.DatasetAnalyzer;
import de.kiaim.model.configuration.anonymization.frontend.FrontendAnonConfig;
import de.kiaim.model.configuration.anonymization.frontend.FrontendAttributeConfig;
import de.kiaim.model.data.DataSet;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.model.enumeration.anonymization.AttributeProtection;
import de.kiaim.model.exception.anonymization.InvalidRiskThresholdException;
import org.bihmi.jal.anon.privacyModels.AverageReidentificationRisk;
import org.bihmi.jal.anon.privacyModels.KAnonymity;
import org.bihmi.jal.anon.privacyModels.PrivacyModel;
import org.bihmi.jal.anon.util.Hierarchy;
import org.bihmi.jal.config.AttributeConfig;
import org.bihmi.jal.config.HierarchyConfig;
import org.bihmi.jal.enums.MicroAggregationFunction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FrontendAnonConfigConverter {

    /**
     * Convert the Risk Threshold Type and Value to a JAL PrivacyModel.
     */
    public static Collection<PrivacyModel> convertPrivacyModels(String riskThresholdType, float riskThresholdValue)
            throws InvalidRiskThresholdException {
        Collection<PrivacyModel> privacyModels = new ArrayList<>();

        // Check riskThresholdType and generate corresponding PrivacyModel
        if (riskThresholdType.equalsIgnoreCase("Max")) {
            // K-Anonymity
            int k = calculateKFromThreshold(riskThresholdValue);
            KAnonymity kAnonymity = new KAnonymity();
            kAnonymity.setK(k);
            privacyModels.add(kAnonymity);
        } else if (riskThresholdType.equalsIgnoreCase("Avg")) {
            AverageReidentificationRisk averageReidentificationRisk = new AverageReidentificationRisk();
            averageReidentificationRisk.setAverageRisk((double) riskThresholdValue);
            privacyModels.add(averageReidentificationRisk);
        }

        return privacyModels;
    }

    /**
     * Calculate k according to risk threshold for Maximum Risk.
     */
    private static int calculateKFromThreshold(float riskThresholdValue)
            throws InvalidRiskThresholdException {
        if (riskThresholdValue == 0.05f) {
            return 20; // 5% risk, hidden among 20
        } else if (riskThresholdValue == 0.075f) {
            return 14; // 7.5% risk, hidden among 14
        } else if (riskThresholdValue == 0.1f) {
            return 10; // 10% risk, hidden among 10
        } else if (riskThresholdValue == 0.2f) {
            return 5;  // 20% risk, hidden among 5
        } else if (riskThresholdValue == 0.5f) {
            return 2;  // 50% risk, hidden among 2
        } else {
            throw new InvalidRiskThresholdException("Invalid risk threshold value.");
        }
    }

    /**
     * Convert a list of FrontendAttributeConfig to a list of JAL AttributeConfig
     */
    public static List<AttributeConfig> convertAttributeConfigs(List<FrontendAttributeConfig> frontendAttributeConfigs, DataSet originalDataSet) {
        List<AttributeConfig> attributeConfigs = new ArrayList<>();

        for (FrontendAttributeConfig frontendConfig : frontendAttributeConfigs) {
            AttributeConfig attributeConfig = new AttributeConfig();
            attributeConfig.setName(frontendConfig.getName());
            attributeConfig.setDataType(frontendConfig.getDataType().toString());
            attributeConfig.setDateFormat(frontendConfig.getDateFormat());
            attributeConfig.setPossibleEntries(frontendConfig.getValues() != null ? frontendConfig.getValues().toArray(new String[0]) : null);

            // Set AttributeType based on attributeProtection
            if (frontendConfig.getAttributeProtection() == AttributeProtection.ATTRIBUTE_DELETION) {
                attributeConfig.setAttributeType("IDENTIFYING_ATTRIBUTE");
            } else if (frontendConfig.getAttributeProtection() == AttributeProtection.NO_PROTECTION) {
                attributeConfig.setAttributeType("INSENSITIVE_ATTRIBUTE");
            } else {
                attributeConfig.setAttributeType("QUASI_IDENTIFYING_ATTRIBUTE");
            }

            // Handle DECIMAL and INTEGER types: Calculate min and max
            if (frontendConfig.getDataType() == DataType.DECIMAL || frontendConfig.getDataType() == DataType.INTEGER) {
                int columnIndex = frontendConfig.getIndex();
                Number[] minMax = DatasetAnalyzer.findMinMaxForColumn(originalDataSet, columnIndex);

                // Set min and max based on the calculated values
                attributeConfig.setMin(minMax[0] != null ? minMax[0] : null);
                attributeConfig.setMax(minMax[1] != null ? minMax[1] : null);
            } else {
                attributeConfig.setMin(null);
                attributeConfig.setMax(null);
            }

            // Handle Micro-Aggregation logic
            if (frontendConfig.getAttributeProtection() == AttributeProtection.MICRO_AGGREGATION) {
                attributeConfig.setUseMicroAggregation(true);
                // Default value ARITHMETIC_MEAN
                attributeConfig.setMicroAggregationFunction(MicroAggregationFunction.ARITHMETIC_MEAN);
            } else {
                attributeConfig.setUseMicroAggregation(false);
            }

            // Generate and set the hierarchy based on attribute configurations
            HierarchyConfig hierarchy = generateHierarchy(frontendConfig);
            attributeConfig.setHierarchyConfig(hierarchy);

            // Add the configured attribute to the list
            attributeConfigs.add(attributeConfig);
        }

        return attributeConfigs;
    }

    /**
     * Generate a hierarchy based on the frontend attribute configuration.
     */
    private static HierarchyConfig generateHierarchy(FrontendAttributeConfig frontendAttributeConfig) {
        String type;
        String intervalSize;
        String splitLevels = ""; // TODO : Should it be set ?
        int minLevelToUse = 0;
        int maxLevelToUse = 0; // TODO : how determine ?

        // Determine hierarchy type and levels based on the attribute's protection, scale, and type
        if (frontendAttributeConfig.getAttributeProtection() == AttributeProtection.DATE_GENERALIZATION) {
            type = "DATES";
            intervalSize = frontendAttributeConfig.getIntervalSize();
//            splitLevels = "year, month/year, week/year";
            minLevelToUse = 0;
            maxLevelToUse = 5; // TODO : Change
        } else if (frontendAttributeConfig.getAttributeProtection() == AttributeProtection.MASKING) {
            type = "MASKING";
            intervalSize = frontendAttributeConfig.getIntervalSize();
            minLevelToUse = 0; // minimal masking
            maxLevelToUse = Integer.parseInt(frontendAttributeConfig.getIntervalSize())+1; // full masking
        } else if ((frontendAttributeConfig.getAttributeProtection() == AttributeProtection.GENERALIZATION
                ||frontendAttributeConfig.getAttributeProtection() == AttributeProtection.MICRO_AGGREGATION)
                && (frontendAttributeConfig.getDataType() == DataType.INTEGER || frontendAttributeConfig.getDataType() == DataType.DECIMAL)) {
            type = "INTERVALS";
            intervalSize = frontendAttributeConfig.getIntervalSize();
            minLevelToUse = 0; // low generalization
            maxLevelToUse = 100; // high generalization
        } else {
            type = "";
            intervalSize= "";
            splitLevels = "";
            minLevelToUse = 0;
            maxLevelToUse = 0;
        }
        // TODO : handle ordering

        // Return the hierarchy configuration
        return HierarchyConfig.builder()
                .attributeName(frontendAttributeConfig.getName())
                .hierarchyType(type)
                .intervalSize(intervalSize)
                .splitLevels(splitLevels)
                .minLevelToUse(minLevelToUse)
                .maxLevelToUse(maxLevelToUse)
                .build();
    }

}

