import { HttpClient } from "@angular/common/http";
import { Injectable } from '@angular/core';
import { TechnicalEvaluationService } from "@features/technical-evaluation/services/technical-evaluation.service";
import { BehaviorSubject, finalize, map, Observable, of, shareReplay, switchMap, tap } from "rxjs";
import { environments } from "src/environments/environment";
import {
    ConfigurationGroupDefinition,
    ConfigurationGroupDefinitions,
    VisualizationType
} from "../model/configuration-group-definition";
import {
    MetricImportance,
    MetricImportanceDefinition,
    MetricSettings,
    ProjectSettings
} from "../model/project-settings";
import { AttributeStatistics, StatisticsValueTypes } from "../model/statistics";

@Injectable({
    providedIn: 'root'
})
export class ProjectConfigurationService {

    private readonly baseUrl: string = environments.apiUrl + "/api/project";

    private _projectSettingsInit$: Observable<ProjectSettings> | null = null;
    private projectSettingsSubject: BehaviorSubject<ProjectSettings | null> = new BehaviorSubject<ProjectSettings | null>(null);

    constructor(
        private readonly http: HttpClient,
        private readonly technicalEvaluationService: TechnicalEvaluationService,
    ) {
    }

    /**
     * Returns an observable for the project settings that does not emit changes.
     */
    public get projectSettings2$(): Observable<ProjectSettings> {
        if (this.projectSettingsSubject.value != null) {
            return of(this.projectSettingsSubject.value);
        } else {
            return this.initProjectSettings$();
        }
    }

    /**
     * Returns an observable for the project settings that emits updates on changes.
     */
    public get projectSettings$(): Observable<ProjectSettings> {
        return this.projectSettingsSubject.asObservable().pipe(
            switchMap(value => {
                if (value) {
                    return of(value);
                } else {
                    return this.initProjectSettings$();
                }
            }),
        );
    }

    public setProjectSettings(value: ProjectSettings): Observable<void> {
        this.projectSettingsSubject!.next(value);
        return this.putProjectSettings(value);
    }

    public getAllMetrics(attributeStatistics: AttributeStatistics): Array<[string, StatisticsValueTypes]> {
        return Object.entries(attributeStatistics.important_metrics).concat(Object.entries(attributeStatistics.details));
    }

    private fetchProjectSettings(): Observable<ProjectSettings> {
        return this.http.get<ProjectSettings>(this.baseUrl + "/configuration");
    }

    private putProjectSettings(projectSettings: ProjectSettings): Observable<void> {
        return this.http.put<void>(this.baseUrl + "/configuration", projectSettings);
    }

    /**
     * Initializes the project settings subject.
     * Fetches the configuration from the backend.
     *
     * @return Shareable observable for fetching the project settings.
     * @private
     */
    private initProjectSettings$(): Observable<ProjectSettings> {
        if (this._projectSettingsInit$ == null) {
            this._projectSettingsInit$ = this.fetchProjectSettings().pipe(
                switchMap(value => {
                    return this.initMetricSettings$(value);
                }),
                tap(value1 => {
                    this.projectSettingsSubject?.next(value1);
                }),
                shareReplay(1),
                finalize(() => {
                    this._projectSettingsInit$ = null;
                }),
            );
        }
        return this._projectSettingsInit$;
    }

    /**
     * Initializes the metric settings if they are not present in the given project settings.
     *
     * @param projectSettings The project settings.
     * @return Observable containing the project settings with initialized metric settings.
     * @private
     */
    private initMetricSettings$(projectSettings: ProjectSettings): Observable<ProjectSettings> {
        if (projectSettings.metricConfiguration) {
            return of(projectSettings);
        } else {
            return this.doInitMetricSettings$().pipe(
                map(value1 => {
                    projectSettings.metricConfiguration = value1;
                    return projectSettings;
                }),
            );
        }
    }

    /**
     * Initializes the metric settings by fetching the definition of all metrics from the technical evaluation
     * and setting each metric to {@link MetricImportance.IMPORTANT}.
     *
     * @return Observable containing the initialized metric settings.
     * @private
     */
    private doInitMetricSettings$(): Observable<MetricSettings> {
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
        metricSettings.useUserDefinedImportance = false;
        metricSettings.userDefinedImportance = {};
        this.createGroups(metricSettings.userDefinedImportance, configurations);
        return metricSettings;
    }

    private createGroups(metricsSettings: MetricImportanceDefinition, configurations: ConfigurationGroupDefinitions) {
        Object.values(configurations).forEach((groupDefinition) => {
            this.createGroup(metricsSettings, groupDefinition);
        });
    }

    private createGroup(metricSettings: MetricImportanceDefinition, groupDefinition: ConfigurationGroupDefinition): void {
        if (groupDefinition.configurations) {
            this.createGroups(metricSettings, groupDefinition.configurations);
        }
        if (groupDefinition.options) {
            Object.entries(groupDefinition.options).forEach(inputDefinition => {
                metricSettings[inputDefinition[0]] = inputDefinition[1].visualization_type === VisualizationType.IMPORTANT_METRICS
                    ? MetricImportance.IMPORTANT
                    : MetricImportance.ADDITIONAL;
            });
        }
    }

}
