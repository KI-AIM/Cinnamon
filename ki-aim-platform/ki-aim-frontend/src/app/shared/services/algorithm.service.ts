import { StepConfiguration } from "../model/step-configuration";
import { Algorithm } from "../model/algorithm";
import { AlgorithmDefinition } from "../model/algorithm-definition";
import { HttpClient } from "@angular/common/http";
import { concatMap, map, Observable, of, tap } from "rxjs";
import { parse } from "yaml";
import { plainToInstance } from "class-transformer";
import { ConfigurationService } from "./configuration.service";
import { ImportPipeData } from "../model/import-pipe-data";
import { environments } from "../../../environments/environment";

export abstract class AlgorithmService {

    private _stepConfig: StepConfiguration | null = null;
    private _algorithms: Algorithm[] | null = null;
    private algorithmDefinitions: {[algorithmName: string]: AlgorithmDefinition} = {};

    private _cachedImportPipeData: ImportPipeData | null = null;

    public doGetConfig: () => Object | string = () => '';
    protected doSetConfig: (config: ImportPipeData) => void = () => { };

    protected constructor(
        private readonly http: HttpClient,
        protected readonly configurationService: ConfigurationService,
    ) {
    }

    /**
     * Name of the step. Must be equal to the name in application.config.
     */
    getStepName: () => string;

    /**
     * Name of the configuration for storing identifying it in the db.
     */
    getConfigurationName: () => string;

    /**
     * Creates the YAML configuration.
     * @param arg The configuration from the form.
     * @param selectedAlgorithm The selected algorithm.
     */
    abstract createConfiguration(arg: Object, selectedAlgorithm: Algorithm): Object;

    /**
     * Extracts the form data and the algorithm name from the given configuration object.
     * @param arg The configuration object.
     */
    abstract readConfiguration(arg: Object): {config: Object, selectedAlgorithm: Algorithm};

    /**
     * Sets the callback function for retrieving the configuration from the UI.
     * @param func The function that is getting called.
     */
    public setDoGetConfig(func: () => string) {
        this.doGetConfig = func;
    }

    /**
     * Sets the callback function for setting the configuration to the UI.
     * @param func The function that is getting called.
     */
    public setDoSetConfig(func: (data: ImportPipeData) => void) {
        this.doSetConfig = func;
    }

    public setConfigWait(value: ImportPipeData) {
        if (this._algorithms !== null) {
            this.doSetConfig(value);
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
     * Returns the definition for the algorithm with the given name.
     * @param algorithmName The algorithm of which the definition should be returned.
     */
    public getAlgorithmDefinitionByName(algorithmName: string): Observable<AlgorithmDefinition> {
        return this.getAlgorithmDefinition(this.getAlgorithmByName(algorithmName));
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
                        return this.loadAlgorithmDefinition(value.url, algorithm)
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
    private get stepConfig(): Observable<StepConfiguration> {
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
                        return this.loadAlgorithms(value.url)
                    }),
                    tap(value =>  {
                        this._algorithms = value
                        if (this._cachedImportPipeData !== null) {
                            this.doSetConfig(this._cachedImportPipeData);
                            this._cachedImportPipeData = null;
                        }
                    }),
                );
        }
        return of(this._algorithms);
    }

    private loadAlgorithms(url: string): Observable<Algorithm[]> {
        return this.stepConfig.pipe(
            concatMap(value => {
                return this.http.get<string>(url + value.algorithmEndpoint, {responseType: 'text' as 'json'})
            }),
            map(value => {
                const response = parse(value) as { [available_synthesizers: string]: Object[] };
                const result: Algorithm[] = [];
                response['algorithms'].forEach(value1 => result.push(plainToInstance(Algorithm, value1)))
                return result;
            }),
        );
    }

    private loadAlgorithmDefinition(url: string, algorithm: Algorithm): Observable<AlgorithmDefinition> {
        return this.http.get<string>(url + algorithm.URL, {responseType: 'text' as 'json'})
            .pipe(map(value => {
                return plainToInstance(AlgorithmDefinition, parse(value));
            }));
    }

    private loadStepConfig(stepName: string): Observable<StepConfiguration> {
        return this.http.get<StepConfiguration>(environments.apiUrl + `/api/step/${stepName}`);
    }
}
