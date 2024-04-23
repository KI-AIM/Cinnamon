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

    /**
     * Whether uploading and downloading a configuration should automatically update the configuration in the database.
     * Set this to false, if you want to write your own synchronization logic.
     * Writing the configuration into the database will be required for loading the application state after logging out and in again.
     */
    syncWithBackend: boolean;

    /**
     * Function that should retrieve the configuration as a YAML string.
     * Gets called when downloading the configuration.
     */
    getConfigCallback: () => string;

    /**
     * Function that should set the configuration in the front end.
     * Gets called when uploading the configuration.
     */
    setConfigCallback: (config: string) => void;
}
