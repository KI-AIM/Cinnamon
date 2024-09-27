import { StepConfiguration } from "../model/step-configuration";
import { Algorithm } from "../model/algorithm";
import { AlgorithmDefinition } from "../model/algorithm-definition";
import { HttpClient } from "@angular/common/http";
import { concatMap, map, Observable, of, tap } from "rxjs";
import { parse, stringify } from "yaml";
import { plainToInstance } from "class-transformer";
import { ConfigurationService } from "./configuration.service";
import { ImportPipeData } from "../model/import-pipe-data";
import { environments } from "../../../environments/environment";

export abstract class AlgorithmService {

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

    abstract getExecStepName(): string;

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
            if (data.yamlConfigString !== "skip") {
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
            return this.stepConfig
                .pipe(
                    concatMap(value => {
                        return this.loadAlgorithmDefinition(value.urlClient, algorithm)
                    }),
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
            return this.loadStepConfig(this.getStepName())
                .pipe(tap(value => this._stepConfig = value));
        }
        return of(this._stepConfig);
    }

    /**
     * Gets all available algorithms for this step.
     */
    public get algorithms(): Observable<Algorithm[]> {
        if (this._algorithms === null) {
            return this.stepConfig
                .pipe(
                    concatMap(value => {
                        return this.loadAlgorithms(value.urlClient)
                    }),
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

    private loadAlgorithms(url: string): Observable<Algorithm[]> {
        return this.stepConfig.pipe(
            concatMap(value => {
                return this.fetchAlgorithms(url + value.algorithmEndpoint);
            }),
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
     * @param url Url.
     * @protected
     */
    protected fetchAlgorithms(url: string): Observable<string> {
        return this.http.get<string>(url, {responseType: 'text' as 'json'});
    }

    private loadAlgorithmDefinition(url: string, algorithm: Algorithm): Observable<AlgorithmDefinition> {
        return this.fetchAlgorithmDefinition(url + algorithm.URL)
            .pipe(map(value => {
                return plainToInstance(AlgorithmDefinition, parse(value));
            }));
    }

    protected fetchAlgorithmDefinition(url: string): Observable<string> {
        return this.http.get<string>(url, {responseType: 'text' as 'json'});
    }

    private loadStepConfig(stepName: string): Observable<StepConfiguration> {
        return this.http.get<StepConfiguration>(environments.apiUrl + `/api/step/${stepName}`);
    }
}

export interface ConfigData {
    formData: Object,
    selectedAlgorithm: Algorithm
}
