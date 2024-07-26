import { Steps } from "src/app/core/enums/steps";
import { Observable } from "rxjs";
import { ImportPipeData } from "./import-pipe-data";

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
     * Function that gets called to fetch the configuration YAML string from the backend.
     * If null when registering, it will be overwritten with the default function.
     */
    fetchConfig: ((configName: string) => Observable<string>) | null;

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
     * Function that gets called to store the configuration string with the backend.
     * If null when registering, it will be overwritten with the default function.
     */
    storeConfig: ((configName: string, yamlConfigString: string) => Observable<Number>) | null;

    /**
     * Function that should retrieve the configuration as a javascript object a YAML string.
     * Gets called when downloading the configuration.
     * The object will be converted into a YAML string before downloading.
     * @returns The configuration as a javascript object or a YAML string.
     */
    getConfigCallback: () => Object | string;

    /**
     * Function that should set the configuration in the front end.
     * Gets called when uploading the configuration.
     * @param config The configurations as a YAML string.
     */
    setConfigCallback: (importData: ImportPipeData) => void;
}
