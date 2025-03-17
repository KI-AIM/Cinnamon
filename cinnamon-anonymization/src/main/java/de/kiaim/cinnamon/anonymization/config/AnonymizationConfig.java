package de.kiaim.cinnamon.anonymization.config;

import lombok.*;
import org.bihmi.jal.anon.JALConfig;
import org.bihmi.jal.anon.privacyModels.PrivacyModel;
import org.bihmi.jal.config.AttributeConfig;
import org.bihmi.jal.config.QualityModelConfig;

import java.util.Collection;
import java.util.List;

/**
 * Class to generate JAL Anonymization Configuration from KI-AIM AnonymizationConfig
 * Uses JAL attributes.
 */
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class AnonymizationConfig {
//    Take in KI AIM specific data configuration or anon configuration etc
//    And change it, so it works for JAL
//    Add default information

    /** List of privacy models to be used for anonymization */
    private final Collection<PrivacyModel> privacyModelList;

    /** Suppression limit */
    private final double suppressionLimit;

    /** Config containing LossMetric and corresponding parameters */
    private final QualityModelConfig qualityModel;

    /** True if local generalization is used */
    private boolean localGeneralization = false;

    /** Iteration performed for local generalization */
    private int localGeneralizationIterations = 1000;

    /** List of attributes and their config */
    private final List<AttributeConfig> attributeConfigs;

    public AnonymizationConfig(Collection<PrivacyModel> privacyModelList,
                               double suppressionLimit,
                               QualityModelConfig qualityModel,
                               boolean localGeneralization,
                               List<AttributeConfig> attributeConfigs) {
        this.privacyModelList = privacyModelList;
        this.suppressionLimit = suppressionLimit;
        this.qualityModel = qualityModel;
        this.localGeneralization = localGeneralization;
        this.attributeConfigs = attributeConfigs;
    }


    // TODO (KO): move to converter?
    public JALConfig toJalConfig(String name){


        try {
            System.out.println("REACHED THIS POINT!!");
            var jalConfig = new JALConfig();
            System.out.println(name);
            jalConfig.setName(name);
            System.out.println(this.privacyModelList);
            jalConfig.setPrivacyModelList(this.privacyModelList);
            System.out.println(this.suppressionLimit);
            jalConfig.setSuppressionLimit(this.suppressionLimit);
            System.out.println(this.qualityModel);
            jalConfig.setQualityModel(this.qualityModel);

//        for (AttributeConfig conf: this.attributeConfigs){
//
//        }
            jalConfig.setAttributeConfigs(attributeConfigs);
            return jalConfig;

        } catch (Exception e){
            e.printStackTrace();
            System.out.println("Exception when creating the config!!");
        }

        return null;
    }

    public JALConfig generateJalAnonymizationConfig(){
        return new JALConfig();
    }
}
