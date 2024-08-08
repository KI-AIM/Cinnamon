import { StepConfiguration } from "../model/step-configuration";
import { Algorithm } from "../model/algorithm";
import { AlgorithmDefinition } from "../model/algorithm-definition";
import { HttpClient } from "@angular/common/http";
import { concatMap, config, map, Observable, of, tap } from "rxjs";
import { parse } from "yaml";
import { plainToInstance } from "class-transformer";
import { ConfigurationService } from "./configuration.service";
import { ImportPipeData } from "../model/import-pipe-data";
import { environments } from "../../../environments/environment";

export abstract class AlgorithmService {

    private _stepConfig: StepConfiguration | null = null;
    private _algorithms: Algorithm[] | null = null;
    private algorithmDefinitions: {[algorithmName: string]: AlgorithmDefinition} = {};

    private _config: string;
    public _getConfig: () => Object | string = () => '';
    public _setConfig: (config: ImportPipeData) => void = () => { console.log(config)};

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

    // abstract createConfiguration(arg: Object, selectedAlgorithm: Algorithm): Object;

    /**
     * Returns the definition for the algorithm with the given name.
     * @param algorithmName The algorithm of which the definition should be returned.
     */
    public getAlgorithmDefinitionByName(algorithmName: string): Observable<AlgorithmDefinition> {
        const algorithm = this._algorithms?.find((value) => value.name === algorithmName)!;
        return this.getAlgorithmDefinition(algorithm);
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
                    tap(value => this._algorithms = value)
                );
        }
        return of(this._algorithms);
    }

    // TODO use url
    private loadAlgorithms(url: string): Observable<Algorithm[]> {
        return this.stepConfig.pipe(
            concatMap(value => {
                return this.http.get<string>(value.algorithmEndpoint, {responseType: 'text' as 'json'})
            }),
            map(value => {
                const response = parse(value) as { [available_synthesizers: string]: Object[] };
                const result: Algorithm[] = [];
                if (response['algorithms']) {
                    response['algorithms'].forEach(value1 => result.push(plainToInstance(Algorithm, value1)))
                } else {
                    response['available_synthesizers'].forEach(value1 => result.push(plainToInstance(Algorithm, value1)))
                }
                return result;
            }),
        );
    }

    // TODO use url
    private loadAlgorithmDefinition(url: string, algorithm: Algorithm): Observable<AlgorithmDefinition> {
        return this.http.get<string>(algorithm.URL, {responseType: 'text' as 'json'})
            .pipe(map(value => {
                console.log(value);
                const todoChange = parse(value);
                if (todoChange['arguments'] == undefined) {
                    todoChange['arguments'] = todoChange["configurations"];
                }
                return plainToInstance(AlgorithmDefinition, todoChange);
            }));
    }

    private loadStepConfig(stepName: string): Observable<StepConfiguration> {
        return this.http.get<StepConfiguration>(environments.apiUrl + `/api/step/${stepName}`);
    }
}
