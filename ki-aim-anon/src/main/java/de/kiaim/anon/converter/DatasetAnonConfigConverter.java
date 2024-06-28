package de.kiaim.anon.converter;

import de.kiaim.anon.config.AnonymizationConfig;
import de.kiaim.model.configuration.anonymization.DatasetAnonymizationConfig;
import de.kiaim.model.data.DataSet;
import org.bihmi.jal.anon.privacyModels.PrivacyModel;
import org.bihmi.jal.config.AttributeConfig;
import org.bihmi.jal.config.QualityModelConfig;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * KI-AIM DatasetAnonymization Converter to JALConfig
 */
@Service
public class DatasetAnonConfigConverter {
//    Take in KI AIM specific data configuration and anon configuration
//    And convert it in JAL Anonymization Config Object

    public AnonymizationConfig convert(DatasetAnonymizationConfig datasetAnonymizationConfig, DataSet dataSet) {

        List<PrivacyModel> privacyModelList = PrivacyModelConverter.convertList(datasetAnonymizationConfig.getAnonymizationConfiguration().getPrivacyModels());

        double suppressionLimit = Double.parseDouble(datasetAnonymizationConfig.getAnonymizationConfiguration().getSuppressionLimit());

        QualityModelConfig qualityModel = QualityModelConverter.convert(datasetAnonymizationConfig.getAnonymizationConfiguration().getQualityModel());

        // Convert attributeConfigs
        List<AttributeConfig> attributeConfigs = AttributeConfigConverter.convertList(
                datasetAnonymizationConfig.getAttributeConfigurations(), dataSet);

        return new AnonymizationConfig(
                privacyModelList,
                suppressionLimit,
                qualityModel,
                datasetAnonymizationConfig.getAnonymizationConfiguration().isLocalGeneralization(),
                100,  // TODO : handle here ?? Default value for localGeneralizationIterations
                attributeConfigs
        );
    }

}
