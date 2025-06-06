package de.kiaim.cinnamon.anonymization.service;

import de.kiaim.cinnamon.anonymization.exception.NoAttributeConfiguredException;
import de.kiaim.cinnamon.anonymization.exception.NoGeneralizationAttributeConfigurationException;
import de.kiaim.cinnamon.model.configuration.anonymization.frontend.FrontendAnonConfig;
import de.kiaim.cinnamon.model.enumeration.anonymization.AttributeProtection;

/**
 * Utility class for validating the configuration of a {@link FrontendAnonConfig}.
 * This class provides static methods to perform various validations on the
 * {@link FrontendAnonConfig}, ensuring that it meets the requirements for the
 * anonymization process. These validations include:
 *     -Checking that at least one attribute configuration is defined.
 *     -Ensuring that at least one attribute configuration is configured with a
 *     generalization or transformation type required by the ARX privacy models.
 */
public class FrontendAnonConfigValidation {

    /**
     * Validates that at least one attribute configuration is defined in the given FrontendAnonConfig.
     *
     * @param frontendAnonConfig The FrontendAnonConfig to validate.
     * @throws NoAttributeConfiguredException If no attribute configuration is defined.
     */
    public static void validateAttributeConfiguration(FrontendAnonConfig frontendAnonConfig) throws NoAttributeConfiguredException {
        if (frontendAnonConfig.getAttributeConfiguration() == null ||
                frontendAnonConfig.getAttributeConfiguration().isEmpty()) {
            throw new NoAttributeConfiguredException();
        }
    }

    /**
     * Validates that at least one attribute configuration is configured with a generalization transformation type.
     * To run ARX with the defined privacy models, at least one attribute should be transformed using
     * "MASKING", "GENERALIZATION", "MICRO-AGGREGATION" or "RECORD-DELETION".
     *
     * @param frontendAnonConfig The FrontendAnonConfig to validate.
     * @throws NoGeneralizationAttributeConfigurationException If no attribute configuration is defined.
     */
    public static void validateOneAttributeIsGeneralized(FrontendAnonConfig frontendAnonConfig)
            throws NoGeneralizationAttributeConfigurationException {

        // Check if at least one attribute has the required protection type
        boolean hasGeneralizedAttribute = frontendAnonConfig.getAttributeConfiguration().stream()
                .anyMatch(attribute -> attribute.getAttributeProtection() != null &&
                        hasGeneralizationTransformation(attribute.getAttributeProtection()));

        if (!hasGeneralizedAttribute) {
            throw new NoGeneralizationAttributeConfigurationException();
        }
    }

    /**
     * Checks if the provided AttributeProtection is one of the generalization transformation types.
     *
     * @param protection The AttributeProtection to check.
     * @return True if it is a generalization transformation type, false otherwise.
     */
    private static boolean hasGeneralizationTransformation(AttributeProtection protection) {
        return protection == AttributeProtection.MASKING ||
                protection == AttributeProtection.GENERALIZATION ||
                protection == AttributeProtection.MICRO_AGGREGATION ||
                protection == AttributeProtection.RECORD_DELETION ||
                protection == AttributeProtection.DATE_GENERALIZATION;
    }
}
