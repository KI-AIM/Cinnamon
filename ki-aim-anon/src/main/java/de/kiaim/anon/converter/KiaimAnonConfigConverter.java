package de.kiaim.anon.converter;

import de.kiaim.anon.config.AnonymizationConfig;
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
public class KiaimAnonConfigConverter {
//    Take in KI AIM specific data configuration and anon configuration
//    And convert it in JAL Anonymization Config Object

    public AnonymizationConfig convert(de.kiaim.model.configuration.anonymization.AnonymizationConfig kiaimAnonConfig, DataSet dataSet) {

        List<PrivacyModel> privacyModelList = PrivacyModelConverter.convertList(kiaimAnonConfig.getPrivacyModels());

        double suppressionLimit = Double.parseDouble(kiaimAnonConfig.getSuppressionLimit());

        QualityModelConfig qualityModel = QualityModelConverter.convert(kiaimAnonConfig.getQualityModel());

        // Convert attributeConfigs
        List<AttributeConfig> attributeConfigs = AttributeConfigConverter.convertList(
                kiaimAnonConfig.getAttributeConfigurations(), dataSet);

        return new AnonymizationConfig(
                privacyModelList,
                suppressionLimit,
                qualityModel,
                kiaimAnonConfig.isLocalGeneralization(),
                attributeConfigs
        );
    }

}
