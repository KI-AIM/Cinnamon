import { Injectable } from '@angular/core';
import { HttpClient } from "@angular/common/http";
import { environments } from "../../../environments/environment";
import { BehaviorSubject, map, Observable, of, shareReplay, switchMap, tap } from "rxjs";
import {MetricImportance, MetricImportanceDefinition, MetricSettings, ProjectSettings} from "../model/project-settings";
import { TechnicalEvaluationService } from "../../features/technical-evaluation/services/technical-evaluation.service";
import { ConfigurationGroupDefinition, ConfigurationGroupDefinitions } from "../model/configuration-group-definition";
import {AttributeStatistics, StatisticsValueTypes} from "../model/statistics";

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

    public getImportantMetrics(attributeStatistics: AttributeStatistics): Array<[string, StatisticsValueTypes]> {
        return Object.entries(attributeStatistics.important_metrics);
    }

    public getDetailMetrics(attributeStatistics: AttributeStatistics): Array<[string, StatisticsValueTypes]> {
        return Object.entries(attributeStatistics.details);
    }

    public getAllMetrics(attributeStatistics: AttributeStatistics): Array<[string, StatisticsValueTypes]> {
        return Object.entries(attributeStatistics.important_metrics).concat(Object.entries(attributeStatistics.details));
    }

    /**
     * Returns all metrics from the given attribute statistics where the configured importance matches the given importance.
     * @param config The metric settings defining the importance of each metric.
     * @param imp The importance of the metrics to filter.
     * @param attributeStatistics The attribute statistics containing the metrics.
     */
    public filterMetrics(config: MetricSettings, imp: MetricImportance, attributeStatistics: AttributeStatistics): Array<[string, StatisticsValueTypes]> {
        if (!config.useUserDefinedImportance) {
            switch (imp) {
                case MetricImportance.IMPORTANT:
                    return this.getImportantMetrics(attributeStatistics);
                case MetricImportance.ADDITIONAL:
                    return this.getDetailMetrics(attributeStatistics);
                case MetricImportance.NOT_RELEVANT:
                    return [];
            }
        }

        return this.getAllMetrics(attributeStatistics).filter(val => {
            return config.userDefinedImportance[val[0]] === imp;
        });
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
            Object.keys(groupDefinition.options).forEach(inputDefinition => {
                metricSettings[inputDefinition] = MetricImportance.IMPORTANT;
            });
        }
    }

}

