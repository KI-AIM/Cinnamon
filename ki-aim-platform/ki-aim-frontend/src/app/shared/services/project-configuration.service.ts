import { Injectable } from '@angular/core';
import { HttpClient } from "@angular/common/http";
import { environments } from "../../../environments/environment";
import { BehaviorSubject, map, Observable, of, shareReplay, switchMap, tap } from "rxjs";
import { MetricImportance, MetricSettings, ProjectSettings } from "../model/project-settings";
import { TechnicalEvaluationService } from "../../features/technical-evaluation/services/technical-evaluation.service";
import { ConfigurationGroupDefinition, ConfigurationGroupDefinitions } from "../model/configuration-group-definition";

@Injectable({
    providedIn: 'root'
})
export class ProjectConfigurationService {

    private readonly baseUrl: string = environments.apiUrl + "/api/project";

    private _projectSettings$: Observable<ProjectSettings> | null = null;
    private projectSettingsSubject: BehaviorSubject<ProjectSettings> | null = null;

    constructor(
        private readonly http: HttpClient,
        private readonly technicalEvaluationService: TechnicalEvaluationService,
    ) {
    }

    private initializeProjectSettings(): void {
        if (!this.projectSettingsSubject) {
            this.projectSettingsSubject = new BehaviorSubject<ProjectSettings>(new ProjectSettings());
            this._projectSettings$ = this.fetchProjectSettings().pipe(
                switchMap(value => {
                   if (value.metricConfiguration) {
                       return of(value);
                   } else {
                       return this.initMetricSettings$().pipe(
                           map(value1 => {
                              value.metricConfiguration = value1;
                              return value;
                           }),
                       );
                   }
                }),
                tap(value => {
                    this.projectSettingsSubject?.next(value);
                }),
                shareReplay(1),
            );
        }
    }

    public get projectSettings$(): Observable<ProjectSettings> {
        this.initializeProjectSettings();
        return this._projectSettings$!;
    }

    public setProjectSettings(value: ProjectSettings): Observable<void> {
        this.projectSettingsSubject!.next(value);
        return this.putProjectSettings(value);
    }

    private fetchProjectSettings(): Observable<ProjectSettings> {
        return this.http.get<ProjectSettings>(this.baseUrl + "/configuration");
    }

    private putProjectSettings(projectSettings: ProjectSettings): Observable<void> {
        return this.http.put<void>(this.baseUrl + "/configuration", projectSettings);
    }

    /**
     * Initializes the metric settings by fetching the definition of all metrics from the technical evaluation
     * and setting each metric to {@link MetricImportance.IMPORTANT}.
     *
     * @return Observable containing the initialized metric settings.
     * @private
     */
    private initMetricSettings$(): Observable<MetricSettings> {
        return this.technicalEvaluationService.algorithms.pipe(
            switchMap(value1 => {
                return this.technicalEvaluationService.getAlgorithmDefinition(value1[0]);
            }),
            map(value1 => {
                return this.initMetricSettings(value1.configurations);
            }),
        );
    }

    private initMetricSettings(configurations: ConfigurationGroupDefinitions): MetricSettings {
        const metricSettings = new MetricSettings();
        this.createGroups(metricSettings, configurations);
        return metricSettings;
    }

    private createGroups(metricsSettings: MetricSettings, configurations: ConfigurationGroupDefinitions) {
        Object.values(configurations).forEach((groupDefinition) => {
            this.createGroup(metricsSettings, groupDefinition);
        });
    }

    private createGroup(metricSettings: MetricSettings, groupDefinition: ConfigurationGroupDefinition): void {
        if (groupDefinition.configurations) {
            this.createGroups(metricSettings, groupDefinition.configurations);
        }
        if (groupDefinition.options) {
            Object.keys(groupDefinition.options).forEach(inputDefinition => {
                metricSettings[inputDefinition] = MetricImportance.IMPORTANT;
            });
        }
    }

}

