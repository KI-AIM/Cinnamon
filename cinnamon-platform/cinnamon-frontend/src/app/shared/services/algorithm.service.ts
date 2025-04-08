import { StepConfiguration } from "../model/step-configuration";
import { Algorithm } from "../model/algorithm";
import { AlgorithmDefinition } from "../model/algorithm-definition";
import { HttpClient } from "@angular/common/http";
import { map, Observable, of, tap } from "rxjs";
import { parse, stringify } from "yaml";
import { plainToInstance } from "class-transformer";
import { ConfigurationService } from "./configuration.service";
import { ImportPipeData } from "../model/import-pipe-data";
import { environments } from "../../../environments/environment";

export abstract class AlgorithmService {
    private readonly baseURL = environments.apiUrl + "/api/config";

    private _stepConfig: StepConfiguration | null = null;
    private _algorithms: Algorithm[] | null = null;
    private algorithmDefinitions: {[algorithmName: string]: AlgorithmDefinition} = {};

    private _cachedImportPipeData: ImportPipeData | null = null;

    private doGetConfig: () => ConfigData = () => {
        return {formData: {}, selectedAlgorithm: new Algorithm()}
    };
    private doSetConfig: (error: string | null) => void = () => { };

    public selectCache: Algorithm | null = null;
    public configCache: {[algorithmName: string]: Object} = {};

    protected constructor(
        private readonly http: HttpClient,
        protected readonly configurationService: ConfigurationService,
    ) {
    }

    /**
     * Name of the step. Must be equal to the name in Spring's application.properties.
     */
    abstract getStepName(): string;

    // TODO fetch
    abstract getConfigurationName(): string;


    abstract getExecStepName(): string;

    /**
     * Name of the jobs to be configured by this configuration page.
     */
    abstract getJobs(): string[];

    /**
     * Creates the YAML configuration object sent to the backend as well as to the external module.
     * @param arg The configuration from the form.
     * @param selectedAlgorithm The selected algorithm.
     */
    abstract createConfiguration(arg: Object, selectedAlgorithm: Algorithm): Object;

    /**
     * Extracts the form data and the algorithm name from the given configuration object.
     * @param arg The configuration object.
     * @param configurationName The key of the configuration.
     */
    abstract readConfiguration(arg: Object, configurationName: string): { config: Object, selectedAlgorithm: Algorithm };

    /**
     * Returns the YAML configuration as a string.
     */
    public getConfig(): string {
        const config = this.doGetConfig();
        return stringify(this.createConfiguration(config.formData, config.selectedAlgorithm));
    }

    /**
     * Sets the configuration form the given data to the UI.
     * @param data Data containing the result of the import.
     */
    public setConfig(data: ImportPipeData): void {
        let error = null;
        if (data.success) {
            if (data.yamlConfigString) {
                const result = this.readConfiguration(parse(data.yamlConfigString), data.configData.name);
                this.selectCache = result.selectedAlgorithm;
                this.configCache[result.selectedAlgorithm.name] = result.config;
            }
        } else {
            error = "Failed to load configuration";
        }

        this.doSetConfig(error);
    }

    /**
     * Sets the callback function for retrieving the configuration from the UI.
     * @param func The function that is getting called.
     */
    public setDoGetConfig(func: () => ConfigData) {
        this.doGetConfig = func;
    }

    /**
     * Sets the callback function for setting the configuration to the UI.
     * @param func The function that is getting called.
     */
    public setDoSetConfig(func: (error: string | null) => void) {
        this.doSetConfig = func;
    }

    /**
     * Sets the configuration if the page is loaded, otherwise caches the result,
     * so it can be loaded after the page is loaded.
     * @param value Result of the configuration import.
     */
    public setConfigWait(value: ImportPipeData) {
        this._cachedImportPipeData = value;
        if (this._algorithms !== null) {
            this.setConfig(value);
        } else {
            this._cachedImportPipeData = value;
        }
    }

    /**
     * Returns the algorithm with the given name.
     * @param algorithmName The name of the algorithm.
     */
    public getAlgorithmByName(algorithmName: string): Algorithm {
        return this._algorithms?.find((value) => value.name === algorithmName)!;
    }

    /**
     * Returns the definition for the given algorithm.
     * @param algorithm The algorithm of which the definition should be returned.
     */
    public getAlgorithmDefinition(algorithm: Algorithm): Observable<AlgorithmDefinition> {
        if (!(algorithm.name in this.algorithmDefinitions)) {
            return this.loadAlgorithmDefinition(algorithm).pipe(
                tap(value => {
                    this.algorithmDefinitions[algorithm.name] = value;
                }),
            );
        }

        return of(this.algorithmDefinitions[algorithm.name]);
    }

    /**
     * Gets the corresponding step configuration.
     * @private
     */
    public get stepConfig(): Observable<StepConfiguration> {
        if (this._stepConfig == null) {
            return this.loadStepConfig(this.getConfigurationName())
                .pipe(tap(value => this._stepConfig = value));
        }
        return of(this._stepConfig);
    }

    /**
     * Gets all available algorithms for this step.
     */
    public get algorithms(): Observable<Algorithm[]> {
        if (this._algorithms === null) {
            return this.loadAlgorithms().pipe(
                tap(value =>  {
                    this._algorithms = value
                    if (this._cachedImportPipeData !== null) {
                        // Fallback if the page load was slower than the request
                        this.setConfig(this._cachedImportPipeData);
                        this._cachedImportPipeData = null;
                    }
                }),
            );
        } else {
            return of(this._algorithms);
        }
    }

    private loadAlgorithms(): Observable<Algorithm[]> {
        return this.fetchAlgorithms().pipe(
            map(value => {
                const response = parse(value) as { [available_synthesizers: string]: Object[] };
                const result: Algorithm[] = [];
                response['algorithms'].forEach(value1 => result.push(plainToInstance(Algorithm, value1)))
                return result;
            }),
        );
    }

    /**
     * Fetches the list of available algorithms as a YAML string.
     * @protected
     */
    protected fetchAlgorithms(): Observable<string> {
        return this.http.get<string>(this.baseURL + "/algorithms", {
            params: {configurationName: this.getConfigurationName()},
            responseType: 'text' as 'json'
        });
    }

    private loadAlgorithmDefinition(algorithm: Algorithm): Observable<AlgorithmDefinition> {
        return this.fetchAlgorithmDefinition(algorithm.URL)
            .pipe(map(value => {
                return plainToInstance(AlgorithmDefinition, parse(value));
            }));
    }

    /**
     * Loads the configuration definition from the given path.
     * @param definitionPath The path for fetching the definition from the external server.
     * @protected
     */
    protected fetchAlgorithmDefinition(definitionPath: string): Observable<string> {
        return this.http.get<string>(this.baseURL + "/algorithm", {
            params: {configurationName: this.getConfigurationName(), definitionPath: definitionPath},
            responseType: 'text' as 'json'
        });
    }

    private loadStepConfig(configName: string): Observable<StepConfiguration> {
        return this.http.get<StepConfiguration>(environments.apiUrl + `/api/step/${configName}`);
    }
}

export interface ConfigData {
    formData: Object,
    selectedAlgorithm: Algorithm
}
