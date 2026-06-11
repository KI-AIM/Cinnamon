import { Injectable } from '@angular/core';
import { AlgorithmService, ReadConfigResult } from "../../../shared/services/algorithm.service";
import { HttpClient } from "@angular/common/http";
import { ConfigurationRegisterData } from "../../../shared/model/configuration-register-data";
import { Steps } from "../../../core/enums/steps";
import { ConfigurationService } from "../../../shared/services/configuration.service";
import { Algorithm } from "../../../shared/model/algorithm";
import { AlgorithmDefinition } from "../../../shared/model/algorithm-definition";
import { map, Observable, ReplaySubject } from "rxjs";
import { parse } from "yaml";
import { plainToInstance } from "class-transformer";

@Injectable({
    providedIn: 'root',
})
export class SynthetizationService extends AlgorithmService {

    /** Path to the study definition served by the synthetization backend. */
    private static readonly STUDY_DEFINITION_PATH = "/hyperparameter_tuning/study.yaml";

    /** Hyperparameter-tuning config set by the synthetization-configuration component. */
    private _hyperparameterConfig: object = { enabled: false };

    /**
     * Emits the hyperparameter-tuning block whenever a configuration is read
     * (import or cached fetch). Replays the latest value so the
     * synthetization-configuration component can hydrate its form even if it
     * subscribes after the config was loaded.
     */
    private readonly _hyperparameterConfigLoaded = new ReplaySubject<any>(1);
    public readonly hyperparameterConfigLoaded$ = this._hyperparameterConfigLoaded.asObservable();

    constructor(
        http: HttpClient,
        configurationService: ConfigurationService,
    ) {
        super(http, configurationService);
    }

    public override getConfigurationName(): string {
        return "synthetization_configuration";
    }

    public setHyperparameterConfig(config: object): void {
        this._hyperparameterConfig = config;
    }

    /** Hyperparameter-tuning config, e.g. for hydrating the form after upload. */
    public getHyperparameterConfig(): any {
        return this._hyperparameterConfig;
    }

    /**
     * Fetches the Optuna study definition (`study.yaml`) as an
     * {@link AlgorithmDefinition} using the same pipeline as per-algorithm
     * configs.
     */
    public loadStudyDefinition(): Observable<AlgorithmDefinition> {
        return this.fetchAlgorithmDefinition(SynthetizationService.STUDY_DEFINITION_PATH).pipe(
            map((value: string) => plainToInstance(AlgorithmDefinition, parse(value))),
        );
    }

    public override createConfiguration(arg: Object, selectedAlgorithm: Algorithm): Object {
        return {
            synthetization_configuration: {
                algorithm: {
                    id: selectedAlgorithm.name,
                    version: selectedAlgorithm.version,
                    synthesizer: selectedAlgorithm.name,
                    type: selectedAlgorithm.type,
                    ...arg,
                    hyperparameter_tuning: this._hyperparameterConfig,
                },
            },
        };
    }

    public override readConfiguration(arg: any, configurationName: string): ReadConfigResult {
        const selectedAlgorithm = this.getAlgorithmByName(arg[configurationName]["algorithm"]["synthesizer"]);
        const config = arg[configurationName]["algorithm"];
        delete config["synthesizer"];
        delete config["type"];
        delete config["version"];

        // Split out the hyperparameter-tuning block so it is applied to the HT
        // form rather than the model-parameter form (and round-trips on export
        // via createConfiguration). An uploaded config may contain this block in
        // addition to the model parameters, or carry only this block.
        if (config["hyperparameter_tuning"]) {
            this.setHyperparameterConfig(config["hyperparameter_tuning"]);
        } else {
            this.setHyperparameterConfig({enabled: false});
        }
        delete config["hyperparameter_tuning"];
        this._hyperparameterConfigLoaded.next(this._hyperparameterConfig);

        return {config, selectedAlgorithm};
    }

    public registerConfig() {
        const configReg = new ConfigurationRegisterData();
        configReg.availableAfterStep = Steps.SYNTHETIZATION;
        configReg.lockedAfterStep = Steps.EXECUTION;
        configReg.displayName = "Synthetization Configuration";
        // TODO fetch from server, user must be logged in for authentication
        configReg.name = "synthetization_configuration";
        configReg.orderNumber = 2;

        this.configurationService.registerConfiguration(configReg);
    }
}
