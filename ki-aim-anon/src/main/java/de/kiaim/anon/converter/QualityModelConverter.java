package de.kiaim.anon.converter;

import org.bihmi.jal.config.QualityModelConfig;

public class QualityModelConverter {
    public static QualityModelConfig convert(String qualityModelString) {
        QualityModelConfig qualityModelConfig = new QualityModelConfig();

        try {
            qualityModelConfig.setQualityModelType(QualityModelConfig.QualityModelType.valueOf(qualityModelString.toUpperCase()));
        } catch (IllegalArgumentException e) {
            //Set default value
            qualityModelConfig.setQualityModelType(QualityModelConfig.QualityModelType.LOSS_METRIC);
        }

        return qualityModelConfig;
    }
}
