import { StepConfiguration } from "../model/step-configuration";
import { Algorithm } from "../model/algorithm";
import { AlgorithmDefinition } from "../model/algorithm-definition";
import { HttpClient } from "@angular/common/http";
import { concatMap, map, Observable, of, tap } from "rxjs";
import { parse } from "yaml";
import { plainToInstance } from "class-transformer";
import { ConfigurationService } from "./configuration.service";

export abstract class AlgorithmService {

    private _stepConfig: StepConfiguration | null = null;
    private _algorithms: Algorithm[] | null = null;
    private algorithmDefinitions: {[algorithmName: string]: AlgorithmDefinition} = {};

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
     * URL to the definition of the given algorithm.
     */
    getDefinitionUrl: (algorithm: Algorithm) => string;

    public getAlgorithmDefinitionByName(algorithmName: string): Observable<AlgorithmDefinition> {
        console.log(algorithmName);
        console.log(this._algorithms);
        const algorithm = this._algorithms?.find((value) => value.name === algorithmName)!;
        console.log(algorithm);
        return this.getAlgorithmDefinition(algorithm);
    }


    public getAlgorithmDefinition(algorithm: Algorithm): Observable<AlgorithmDefinition> {
        if (!(algorithm.name in this.algorithmDefinitions)) {
            return this.stepConfig
                .pipe(
                    concatMap(value => {
                        return this.loadAlgorithmDefinition(value.url, algorithm)
                    }),
                    tap(value => this.algorithmDefinitions[algorithm.name] = value)
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
        return this.http.get<string>('/get_algorithms', {responseType: 'text' as 'json'})
            .pipe(
                map(value => {
                    const response = parse(value) as { [available_synthesizers: string]: Object[] };
                    const result: Algorithm[] = [];
                    response['available_synthesizers'].forEach(value1 => result.push(plainToInstance(Algorithm, value1)))
                    return result;
                })
            );
    }

    // TODO use url
    private loadAlgorithmDefinition(url: string, algorithm: Algorithm): Observable<AlgorithmDefinition> {
        return this.http.get<string>(this.getDefinitionUrl(algorithm), {responseType: 'text' as 'json'})
            .pipe(map(value => {
                return plainToInstance(AlgorithmDefinition, parse(value))
            }));
    }

    private loadStepConfig(stepName: string): Observable<StepConfiguration> {
        return this.http.get<StepConfiguration>(`api/step/${stepName}`);
    }
}
