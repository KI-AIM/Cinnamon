package de.kiaim.cinnamon.model.enumeration.anonymization;

import java.util.Arrays;

public enum AttributeProtection {
    ATTRIBUTE_DELETION,
    GENERALIZATION,
    MICRO_AGGREGATION,
    DATE_GENERALIZATION,
    VALUE_DELETION,
    RECORD_DELETION,
    MASKING,
    NO_PROTECTION
    ;

    /**
     * Returns the display name of the attribute protection based on the enum name.
     *
     * @return The display name.
     */
    public String getDisplayName() {
        return Arrays.stream(this.name().split("_"))
                     .map(word -> word.charAt(0) + word.substring(1).toLowerCase())
                     .reduce("", (a, b) -> a + " " + b);
    }
}
