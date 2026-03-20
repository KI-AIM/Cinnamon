import { Steps } from "src/app/core/enums/steps";

/**
 * Data for registering a configuration.
 */
export class ConfigurationRegisterData {
    /**
     * Step after the configuration upload and download should be available.
     */
    availableAfterStep: Steps;

    /**
     * Name that is displayed in the UI.
     */
    displayName: string;

    /**
     * Step until the configuration upload and download should be available.
     */
    lockedAfterStep: Steps | null;

    /**
     * Identifier that is used to identify the configuration in the database.
     */
    name: string;

    /**
     * Position the configuration int the UI.
     */
    orderNumber: number;
}
