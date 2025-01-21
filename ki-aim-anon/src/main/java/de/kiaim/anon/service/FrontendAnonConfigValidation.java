package de.kiaim.anon.service;

import de.kiaim.anon.exception.NoAttributeConfiguredException;
import de.kiaim.model.configuration.anonymization.frontend.FrontendAnonConfig;

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
}
