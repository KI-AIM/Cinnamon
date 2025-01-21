package de.kiaim.anon.converter;

import de.kiaim.anon.config.AnonymizationConfig;
import de.kiaim.anon.exception.ConvertToJALConfigException;
import de.kiaim.anon.helper.DatasetAnalyzer;
import de.kiaim.model.configuration.anonymization.frontend.FrontendAnonConfig;
import de.kiaim.model.configuration.anonymization.frontend.FrontendAttributeConfig;
import de.kiaim.model.configuration.data.*;
import de.kiaim.model.data.DataSet;
import de.kiaim.model.enumeration.DataScale;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.model.enumeration.anonymization.AttributeProtection;
import de.kiaim.model.exception.anonymization.InvalidAttributeConfigException;
import de.kiaim.model.exception.anonymization.InvalidGeneralizationSettingException;
import de.kiaim.model.exception.anonymization.InvalidRiskThresholdException;
import de.kiaim.model.exception.anonymization.InvalidSuppressionLimitException;
import org.bihmi.jal.anon.privacyModels.AverageReidentificationRisk;
import org.bihmi.jal.anon.privacyModels.KAnonymity;
import org.bihmi.jal.anon.privacyModels.PrivacyModel;
import org.bihmi.jal.config.AttributeConfig;
import org.bihmi.jal.config.HierarchyConfig;
import org.bihmi.jal.config.QualityModelConfig;
import org.bihmi.jal.enums.MicroAggregationFunction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FrontendAnonConfigConverter {

    /**
     * Convert a FrontendAnonConfig to a JAL AnonymizationConfig.
     *
     * @param frontendConfig The configuration received from the frontend.
     * @param originalDataSet The dataset associated with this configuration.
     * @return An AnonymizationConfig object configured for JAL.
     * @throws InvalidRiskThresholdException If the risk threshold provided is invalid.
     */
    public static AnonymizationConfig convertToJALConfig(FrontendAnonConfig frontendConfig, DataSet originalDataSet)
            throws ConvertToJALConfigException {

        // Validate the configuration before conversion
        try {
            frontendConfig.validateConfig();

            // Convert the Risk Threshold to Privacy Models
            Collection<PrivacyModel> privacyModels = convertPrivacyModels(
                    frontendConfig.getPrivacyModels().get(0).getModelConfiguration().getRiskThresholdType(),
                    frontendConfig.getPrivacyModels().get(0).getModelConfiguration().getRiskThresholdValue());

            // Convert the list of FrontendAttributeConfig to AttributeConfig for JAL
            List<AttributeConfig> attributeConfigs = convertAttributeConfigs(
                    frontendConfig.getAttributeConfiguration(), originalDataSet);

            // Create a QualityModelConfig with default values
            QualityModelConfig qualityModelConfig = new QualityModelConfig();
            qualityModelConfig.setQualityModelType(QualityModelConfig.QualityModelType.LOSS_METRIC);

            // Create the AnonymizationConfig object

            return new AnonymizationConfig(
                    privacyModels,
                    Double.parseDouble(frontendConfig.getPrivacyModels().get(0).getModelConfiguration().getSuppressionLimit()), // Suppression limit between 0 and 1
                    qualityModelConfig, // Assuming QualityModelConfig needs a setting like "Global" or "Local"
                    frontendConfig.getPrivacyModels().get(0).getModelConfiguration().getGeneralizationSetting().equalsIgnoreCase("Local"), // Local generalization is true if setting is "Local"
                    attributeConfigs
            );
        }catch (InvalidRiskThresholdException e) {
            throw new ConvertToJALConfigException("Invalid risk threshold: " + e.getMessage());
        } catch (InvalidAttributeConfigException e) {
            throw new ConvertToJALConfigException("Invalid attribute configuration: " + e.getMessage());
        } catch (InvalidGeneralizationSettingException e) {
            throw new ConvertToJALConfigException("Invalid generalization setting: " + e.getMessage());
        } catch (InvalidSuppressionLimitException e) {
            throw new ConvertToJALConfigException("Invalid suppression limit: " + e.getMessage());
        }
    }

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
    public static List<AttributeConfig> convertAttributeConfigs(List<FrontendAttributeConfig> frontendAttributeConfigs,
                                                                DataSet originalDataSet)
            throws InvalidAttributeConfigException {
        List<AttributeConfig> attributeConfigs = new ArrayList<>();

        // Generate an anon configuration for each column of the dataset
        for (ColumnConfiguration columnConfig : originalDataSet.getDataConfiguration().getConfigurations()) {
            // Check if attribute configured by user
            FrontendAttributeConfig matchingFrontendConfig = frontendAttributeConfigs.stream()
                    .filter(frontendConfig -> frontendConfig.getName().equals(columnConfig.getName()))
                    .findFirst()
                    .orElse(null);

            // If attribute is in frontend config, generate JAL config from frontend config
            if (matchingFrontendConfig != null) {
                AttributeConfig attributeConfig = createAttributeConfigFromFrontendConfig(matchingFrontendConfig, originalDataSet);
                attributeConfigs.add(attributeConfig);
            } else {
                // If attribute not in frontend config, generate a default JAL attribute config
                AttributeConfig defaultConfig = createDefaultAttributeConfig(columnConfig);
                attributeConfigs.add(defaultConfig);
            }
        }

        return attributeConfigs;
    }

    /**
     * Generate JAL attribute config from config defined by the user in the frontend.
     */
    private static AttributeConfig createAttributeConfigFromFrontendConfig(FrontendAttributeConfig frontendConfig,
                                                                           DataSet originalDataSet)
            throws InvalidAttributeConfigException {
        AttributeConfig attributeConfig = new AttributeConfig();
        attributeConfig.setName(frontendConfig.getName());
        attributeConfig.setDataType(frontendConfig.getDataType().toString());
        attributeConfig.setDateFormat(frontendConfig.getDateFormat());
        attributeConfig.setPossibleEntries(frontendConfig.getValues() != null && frontendConfig.getValues().length > 0 ? frontendConfig.getValues() : null);

        // Set AttributeType based on attributeProtection
        if (frontendConfig.getAttributeProtection() == AttributeProtection.ATTRIBUTE_DELETION) {
            attributeConfig.setAttributeType("IDENTIFYING_ATTRIBUTE");
            attributeConfig.setHierarchyConfig(null);
        } else if (frontendConfig.getAttributeProtection() == AttributeProtection.NO_PROTECTION) {
            attributeConfig.setAttributeType("INSENSITIVE_ATTRIBUTE");
            attributeConfig.setHierarchyConfig(null);
        } else {
            attributeConfig.setAttributeType("QUASI_IDENTIFYING_ATTRIBUTE");
            // Generate and set the hierarchy based on attribute configurations
            HierarchyConfig hierarchy = generateHierarchy(frontendConfig, originalDataSet.getDataConfiguration());
            attributeConfig.setHierarchyConfig(hierarchy);
        }

        // Handle DECIMAL and INTEGER types: Calculate min and max
        if (frontendConfig.getDataType() == DataType.DECIMAL || frontendConfig.getDataType() == DataType.INTEGER) {
            int columnIndex = frontendConfig.getIndex();
            Number[] minMax = DatasetAnalyzer.findMinMaxForColumn(originalDataSet, columnIndex);

            attributeConfig.setMin(minMax[0] != null ? minMax[0] : null);
            attributeConfig.setMax(minMax[1] != null ? minMax[1] : null);
        } else {
            attributeConfig.setMin(null);
            attributeConfig.setMax(null);
        }

        // Handle Micro-Aggregation logic
        if (frontendConfig.getAttributeProtection() == AttributeProtection.MICRO_AGGREGATION) {
            attributeConfig.setUseMicroAggregation(true);
            attributeConfig.setMicroAggregationFunction(MicroAggregationFunction.ARITHMETIC_MEAN); // Default Value
        } else {
            attributeConfig.setUseMicroAggregation(false);
        }

        return attributeConfig;
    }

    /**
     * Generate default attribute config for attributes not configured.
     * For now the default config delete the attribute.
     * TODO : change to Default generalization
     */
    private static AttributeConfig createDefaultAttributeConfig(ColumnConfiguration columnConfig) throws InvalidAttributeConfigException {
        AttributeConfig defaultConfig = new AttributeConfig();
        defaultConfig.setName(columnConfig.getName());
        defaultConfig.setDataType(columnConfig.getType().toString());

        // TODO : change to generalize ? improve default hierarchy
        defaultConfig.setAttributeType("IDENTIFYING_ATTRIBUTE"); //By default, attribute is deleted
        defaultConfig.setHierarchyConfig(null);

        if (columnConfig.getType() == DataType.INTEGER || columnConfig.getType() == DataType.DECIMAL) {
            defaultConfig.setMin(null);
            defaultConfig.setMax(null);
        }

        String dateformat = null;
        if ((columnConfig.getType() == DataType.DATE) ||
                (columnConfig.getType() == DataType.DATE_TIME)) {
            for (Configuration config : columnConfig.getConfigurations()) {
                if (config instanceof DateFormatConfiguration ) {
                    DateFormatConfiguration dateConfig = (DateFormatConfiguration) config;
                    dateformat = dateConfig.getDateFormatter();
                }
                else if (config instanceof DateTimeFormatConfiguration) {
                    DateTimeFormatConfiguration dateConfig = (DateTimeFormatConfiguration) config;
                    dateformat = dateConfig.getDateTimeFormatter();
                }
            }
            if (dateformat == null) {
                throw new ConvertToJALConfigException("dateFormat could not be retrieved from DataConfiguration, a DateFormat must be provided for DATE or DATE_TIME attributes.");
            }
        }
        defaultConfig.setDateFormat(dateformat);

        // TODO : handle Categorical data

        defaultConfig.setUseMicroAggregation(false);

        return defaultConfig;
    }

    /**
     * Generate a hierarchy based on the frontend attribute configuration.
     */
    private static HierarchyConfig generateHierarchy(FrontendAttributeConfig frontendAttributeConfig, DataConfiguration originalDatasetConfiguration) throws InvalidAttributeConfigException {
        String type;
        String intervalSize;
        String splitLevels = ""; // TODO : Should it be set ? not yet
        String dateformat = null;
        int minLevelToUse = 0;
        int maxLevelToUse = 1; // TODO : how determine ? Not use now : target level = interval size

        // Determine hierarchy type and levels based on the attribute's protection, scale, and type
        if (frontendAttributeConfig.getAttributeProtection() == AttributeProtection.DATE_GENERALIZATION) {
            type = "DATES";

            // Retrieve DateFormat
            ColumnConfiguration columnConfig = originalDatasetConfiguration.getColumnConfigurationByColumnName(frontendAttributeConfig.getName());

            for (Configuration config : columnConfig.getConfigurations()) {
                if (config instanceof DateFormatConfiguration ) {
                    DateFormatConfiguration dateConfig = (DateFormatConfiguration) config;
                    dateformat = dateConfig.getDateFormatter();
                }
                else if (config instanceof DateTimeFormatConfiguration) {
                    DateTimeFormatConfiguration dateConfig = (DateTimeFormatConfiguration) config;
                    dateformat = dateConfig.getDateTimeFormatter();
                }
            }
            if (dateformat == null) {
                throw new ConvertToJALConfigException("dateFormat could not be retrieved from DataConfiguration, a DateFormat must be provided for DATE or DATE_TIME attributes.");
            }
            intervalSize = frontendAttributeConfig.getIntervalSize();
            minLevelToUse = 0;
            maxLevelToUse = 1; // TODO : Change
        } else if (frontendAttributeConfig.getAttributeProtection() == AttributeProtection.MASKING) {
            type = "MASKING";
            intervalSize = frontendAttributeConfig.getIntervalSize();
            minLevelToUse = 0; // minimal masking
            maxLevelToUse = Integer.parseInt(frontendAttributeConfig.getIntervalSize()); // full masking
        } else if ((frontendAttributeConfig.getAttributeProtection() == AttributeProtection.GENERALIZATION
                ||frontendAttributeConfig.getAttributeProtection() == AttributeProtection.MICRO_AGGREGATION)
                && (frontendAttributeConfig.getDataType() == DataType.INTEGER || frontendAttributeConfig.getDataType() == DataType.DECIMAL)) {
            type = "INTERVALS";
            intervalSize = frontendAttributeConfig.getIntervalSize();
            minLevelToUse = 0; // low generalization
            maxLevelToUse = 2; // high generalization
        } else if ((frontendAttributeConfig.getAttributeProtection() == AttributeProtection.GENERALIZATION
                ||frontendAttributeConfig.getAttributeProtection() == AttributeProtection.MICRO_AGGREGATION)
                && (frontendAttributeConfig.getScale() == DataScale.ORDINAL)) {
            type = "ORDERING";
            intervalSize = frontendAttributeConfig.getIntervalSize();
            minLevelToUse = 0; // low generalization
            maxLevelToUse = 100; // high generalization
        } else if (frontendAttributeConfig.getAttributeProtection() == AttributeProtection.RECORD_DELETION) {
            type = "SUPPRESSION";
            intervalSize= "";
            splitLevels = "";
            minLevelToUse = 0;
            maxLevelToUse = 1;
        } else {
            type = "";
            intervalSize= "";
            splitLevels = "";
            minLevelToUse = 0;
            maxLevelToUse = 2;
        }

        // Return the hierarchy configuration
        return HierarchyConfig.builder()
                .attributeName(frontendAttributeConfig.getName())
                .hierarchyType(type)
                .intervalSize(intervalSize)
                .dateFormat(dateformat)
                .splitLevels(splitLevels)
                .minLevelToUse(minLevelToUse)
                .maxLevelToUse(maxLevelToUse)
                .build();
    }

}

