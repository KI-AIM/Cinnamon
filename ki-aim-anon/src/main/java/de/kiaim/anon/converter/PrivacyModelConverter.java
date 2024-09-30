package de.kiaim.anon.converter;

import de.kiaim.model.configuration.anonymization.AverageReidentificationRisk;
import de.kiaim.model.configuration.anonymization.KAnonymity;
import de.kiaim.model.configuration.anonymization.PopulationUniqueness;
import org.bihmi.jal.anon.privacyModels.PrivacyModel;

import java.util.ArrayList;
import java.util.List;

public class PrivacyModelConverter {
    public static PrivacyModel convert(de.kiaim.model.configuration.anonymization.PrivacyModel kiPrivacyModel) {
        if (kiPrivacyModel instanceof KAnonymity) {
            org.bihmi.jal.anon.privacyModels.KAnonymity jalKAnonymity = new org.bihmi.jal.anon.privacyModels.KAnonymity();
            jalKAnonymity.setK(((KAnonymity) kiPrivacyModel).getK());
            return jalKAnonymity;
        }

        else if (kiPrivacyModel instanceof AverageReidentificationRisk) {
            org.bihmi.jal.anon.privacyModels.AverageReidentificationRisk jalAverageReidentificationRisk = new org.bihmi.jal.anon.privacyModels.AverageReidentificationRisk();
            jalAverageReidentificationRisk.setAverageRisk(((AverageReidentificationRisk) kiPrivacyModel).getAverageRisk());
            return jalAverageReidentificationRisk;
        }

        // TODO (A): Add other PrivacyModel types, population uniqueness
        throw new IllegalArgumentException("Unsupported PrivacyModel type: " + kiPrivacyModel.getClass().getName());
    }

    public static List<PrivacyModel> convertList(List<de.kiaim.model.configuration.anonymization.PrivacyModel> kiPrivacyModels) {
        List<PrivacyModel> jalPrivacyModels = new ArrayList<>();
        for (de.kiaim.model.configuration.anonymization.PrivacyModel kiPrivacyModel : kiPrivacyModels) {
            jalPrivacyModels.add(convert(kiPrivacyModel));
        }
        return jalPrivacyModels;
    }
}
