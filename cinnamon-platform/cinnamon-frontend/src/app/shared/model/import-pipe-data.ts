import { ConfigurationRegisterData } from "./configuration-register-data";
import { HttpErrorResponse } from "@angular/common/http";

export class ImportPipeDataIntern {
    name: string;
    configData: ConfigurationRegisterData | null;
    yamlConfigString: string | null;
    error: HttpErrorResponse | null = null;
    success: boolean;
}

export class ImportPipeData {
    /**
     * Registered name of the configuration
     */
    name: string;

    /**
     * Registered data of teh configuration
      */
    configData: ConfigurationRegisterData;

    /**
     * The configuration as YAML string
     */
    yamlConfigString: string;

    /**
     * Error during the import.
     */
    error: HttpErrorResponse | null = null;

    /**
     * If the import was successful.
     */
    success: boolean;
}
