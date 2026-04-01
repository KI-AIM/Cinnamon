import { ConfigurationObject } from "@shared/model/anonymization-attribute-config";
import { Algorithm } from "../model/algorithm";
import { AlgorithmDefinition } from "../model/algorithm-definition";
import { HttpClient } from "@angular/common/http";
import { filter, map, Observable, of, switchMap, tap } from "rxjs";
import { parse } from "yaml";
import { plainToInstance } from "class-transformer";
import { ConfigurationService } from "./configuration.service";
import { ConfigurationImportSummaryPart } from "../model/import-pipe-data";
import { environments } from "src/environments/environment";

export abstract class AlgorithmService {
    private readonly baseURL = environments.apiUrl + "/api/config";

    private _algorithms: Algorithm[] | null = null;
    private algorithmDefinitions: {[algorithmName: string]: AlgorithmDefinition} = {};

    private _cachedImportPipeData: ConfigurationImportSummaryPart | null = null;

    private doSetConfig: (error: string | null) => void = () => { };

    protected constructor(
        private readonly http: HttpClient,
        protected readonly configurationService: ConfigurationService,
    ) {
        configurationService.configImport$.pipe(
            filter(value => value.configurationName === this.getConfigurationName()),
            filter(value => value.configuration !== null),
        ).subscribe(value => {
            if (this._algorithms !== null) {
                this.setConfig(value);
            } else {
                this._cachedImportPipeData = value;
            }
        });
    }

    /**
     * Name of the configuration to be configured.
     */
    abstract getConfigurationName(): string;

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
    abstract readConfiguration(arg: Object, configurationName: string): ReadConfigResult;

    /**
     * Fetches the configuration information from the backend.
     */
    public fetchInfo(): Observable<ConfigurationInfo> {
        return this.http.get<ConfigurationInfo>(this.baseURL + "/info", {
            params: {name: this.getConfigurationName()},
        });
    }

    public fetchConfiguration(): Observable<ConfigData> {
        const cachedAlgorithm = this.configurationService.getSelectedAlgorithm(this.getConfigurationName());
        const cachedConfig = this.configurationService.getSelectedConfiguration(this.getConfigurationName());
        if (cachedConfig != null && cachedAlgorithm != null) {
            return of({config: cachedConfig, selectedAlgorithm: cachedAlgorithm});
        }

        return this.fetchInfo().pipe(
            switchMap(value => {
                // Either all processes are configured or none, so look up if the first is configured
                if (value.processes[0].configured) {
                    // Ensure algorithms are loaded before fetching the configuration
                    return this.algorithms.pipe(
                        switchMap(_ => {
                            return this.doFetchConfiguration();
                        }),
                    );
                } else {
                    return of({config: {}, selectedAlgorithm: null});
                }
            }),
        );
    }

    /**
     * Returns the selected algorithm, its definition and the configuration object for the process.
     * @returns Observable for the selected algorithm and configuration.
     */
    public getAlgorithmData$(): Observable<AlgorithmData> {
        return this.fetchConfiguration().pipe(
            switchMap(value => {
                if (value.selectedAlgorithm === null) {
                    return of({
                        config: value.config,
                        selectedAlgorithm: null,
                        algorithmDefinition: null,
                    });
                } else {
                    return this.getAlgorithmDefinition(value.selectedAlgorithm).pipe(
                        map(def => {
                            return {
                                config: value.config,
                                selectedAlgorithm: value.selectedAlgorithm,
                                algorithmDefinition: def
                            }
                        })
                    );
                }

            }),
        );
    }

    /**
     * Sets the configuration from the given data to the UI.
     * @param data Data containing the result of the import.
     */
    public setConfig(data: ConfigurationImportSummaryPart): void {
        const result = this.readConfiguration(data.configuration!, data.configurationName);
        this.configurationService.setSelectedAlgorithm(this.getConfigurationName(), result.selectedAlgorithm);
        this.configurationService.setConfiguration(this.getConfigurationName(), result.selectedAlgorithm, result.config);

        this.doSetConfig(null);
    }

    /**
     * Sets the callback function for setting the configuration to the UI.
     * @param func The function that is getting called.
     */
    public setDoSetConfig(func: (error: string | null) => void) {
        this.doSetConfig = func;
    }

    /**
     * Returns the algorithm with the given name.
     *
     * @param algorithmName The name of the algorithm.
     * @return The algorithm object.
     * @throws Error if no algorithm with the name can be found.
     */
    public getAlgorithmByName(algorithmName: string): Algorithm {
        const algorithm = this._algorithms?.find((value) => value.name === algorithmName);
        if (algorithm == null) {
            throw new Error("No algorithm with name '" + algorithmName + "' available.");
        }
        return algorithm;
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

    /**
     * Deleted cached configuration definitions.
     */
    public invalidateCachedAlgorithmDefinitions(): void {
        this.algorithmDefinitions = {};
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
            responseType: 'text' as 'json',
            headers: {'Accept': 'application/yaml'},
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
            responseType: 'text' as 'json',
            headers: {'Accept': 'application/yaml'},
        });
    }

    /**
     * Fetches the selected algorithm and configuration defined by {@link getConfigurationName}.
     * Prepares the configuration to be used in a form by removing additional meta-information.
     *
     * @return The algorithm and configuration.
     */
    private doFetchConfiguration(): Observable<ConfigData> {
        return this.configurationService.loadConfig(this.getConfigurationName()).pipe(
            map(value => {
                return this.readConfiguration(value, this.getConfigurationName());
            }),
        );
    }
}

/**
 * Information for the configuration page.
 */
export interface ConfigurationInfo {
    /**
     * Processes to be configured by this configuration page.
     */
    processes: ProcessInfo[]
}

/**
 * Information about a process.
 */
export interface ProcessInfo {
    /**
     * Name of the job to be configured by this configuration page.
     */
    job: string
    /**
     * If the job should be skipped.
     */
    skip: boolean
    /**
     * If the job does not need a hol-out split or the hold-out split is present.
     */
    holdOutFulfilled: boolean
    /**
     * If the process is configured.
     */
    configured: boolean
}

export interface ConfigData {
    config: ConfigurationObject,
    selectedAlgorithm: Algorithm | null
}

export interface ReadConfigResult {
    config: ConfigurationObject,
    selectedAlgorithm: Algorithm
}

/**
 * Contains all information about the selected algorithm and the applied configuration.
 * Other classes can extend this interface and overwrite the config field with a specialized configuration object.
 */
export interface AlgorithmData {
    config: ConfigurationObject,
    selectedAlgorithm: Algorithm | null
    algorithmDefinition: AlgorithmDefinition | null
}
