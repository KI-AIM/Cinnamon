package de.kiaim.cinnamon.anonymization.exception;

public class NoGeneralizationAttributeConfigurationException extends AnonymizationException {
    public NoGeneralizationAttributeConfigurationException() {
        super("ANON_1_2_2", "You need to configure at least one attribute with a Transformation Type of MASKING, " +
                "GENERALIZATION, MICRO_AGGREGATION, DATE_GENERALIZATION or RECORD_DELETION to apply the selected Privacy Model.");
    }
}
