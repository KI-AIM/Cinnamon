import { Injectable } from '@angular/core';
import { AlgorithmService, ReadConfigResult } from "../../../shared/services/algorithm.service";
import { HttpClient } from "@angular/common/http";
import { ConfigurationRegisterData } from "../../../shared/model/configuration-register-data";
import { Steps } from "../../../core/enums/steps";
import { ConfigurationService } from "../../../shared/services/configuration.service";
import { Algorithm, isStructuredOnlySynthesizer } from "../../../shared/model/algorithm";
import { AlgorithmDefinition } from "../../../shared/model/algorithm-definition";
import { map, Observable, ReplaySubject } from "rxjs";
import { parse } from "yaml";
import { plainToInstance } from "class-transformer";
import { environments } from "src/environments/environment";

interface NamedListSuggestionResponse {
    items: Array<{name: string, description: string}>;
}

@Injectable({
    providedIn: 'root',
})
export class SynthetizationService extends AlgorithmService {

    /** Path to the study definition served by the synthetization backend. */
    private static readonly STUDY_DEFINITION_PATH = "/hyperparameter_tuning/study.yaml";
    private static readonly NAMED_LIST_SUGGESTION_PATH = "/api/config/synthetization/named-list";

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
        private readonly httpClient: HttpClient,
        configurationService: ConfigurationService,
    ) {
        super(httpClient, configurationService);
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

    public suggestNamedList(config: object, listName: string): Observable<Array<{name: string, description: string}>> {
        return this.httpClient.post<NamedListSuggestionResponse>(
            `${environments.apiUrl}${SynthetizationService.NAMED_LIST_SUGGESTION_PATH}/${listName}/suggest`,
            config,
        ).pipe(
            map(response => response.items ?? []),
        );
    }

    public override createConfiguration(arg: Object, selectedAlgorithm: Algorithm): Object {
        const formData = {...(arg as any)};
        const textSynthesisConfiguration = formData["text_synthesis_configuration"];
        delete formData["text_synthesis_configuration"];

        const config: any = {
            synthetization_configuration: {
                algorithm: {
                    id: selectedAlgorithm.name,
                    synthesizer: selectedAlgorithm.name,
                    type: selectedAlgorithm.type,
                    version: selectedAlgorithm.version,
                    ...(isStructuredOnlySynthesizer(selectedAlgorithm)
                        ? {hyperparameter_tuning: this._hyperparameterConfig}
                        : {}),
                    ...formData,
                },
            },
        };

        if (textSynthesisConfiguration) {
            config["synthetization_configuration"]["text_synthesis_configuration"] = textSynthesisConfiguration;
        }

        return config;
    }

    public override readConfiguration(arg: any, configurationName: string): ReadConfigResult {
        const selectedAlgorithm = this.getAlgorithmByName(arg[configurationName]["algorithm"]["synthesizer"]);
        const config = {...arg[configurationName]["algorithm"]};
        const textSynthesisConfiguration =
            arg[configurationName]["text_synthesis_configuration"]
            ?? arg["text_synthesis_configuration"];
        delete config["id"];
        delete config["synthesizer"];
        delete config["type"];
        delete config["version"];

        if (textSynthesisConfiguration != null) {
            config["text_synthesis_configuration"] = textSynthesisConfiguration;
        }

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
