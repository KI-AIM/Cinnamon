import { Component, Input, OnDestroy, OnInit, TemplateRef } from '@angular/core';
import {
    debounceTime,
    distinctUntilChanged,
    filter, map,
    Observable, Subscription,
    switchMap,
    take,
    tap,
    timer
} from "rxjs";
import { Statistics } from "../../model/statistics";
import { StatisticsService } from "../../services/statistics.service";
import { Steps } from "../../../core/enums/steps";
import { MatDialog } from "@angular/material/dialog";
import { AlgorithmDefinition } from "../../model/algorithm-definition";
import {
    TechnicalEvaluationService
} from "../../../features/technical-evaluation/services/technical-evaluation.service";
import { FormControl, FormGroup, Validators } from "@angular/forms";
import { ConfigurationGroupDefinition } from "../../model/configuration-group-definition";
import { MetricImportance, MetricImportanceData } from "../../model/project-settings";
import { ProjectConfigurationService } from "../../services/project-configuration.service";

@Component({
    selector: 'app-data-inspection',
    templateUrl: './data-inspection.component.html',
    styleUrls: ['./data-inspection.component.less']
})
export class DataInspectionComponent implements OnInit, OnDestroy {
    @Input() public sourceDataset: string | null = null;
    @Input() public sourceProcess: string | null = null;
    @Input() public mainData: 'real' | 'synthetic' = 'real';
    @Input() public processingSteps: Steps[] = [];

    protected statistics$: Observable<Statistics | null>;

    protected filterText: string;

    protected algorithmDefinition$: Observable<AlgorithmDefinition>;
    protected importanceForm: FormGroup;

    private updateSubscription: Subscription | null = null;

    constructor(
        private readonly technicalEvaluationService: TechnicalEvaluationService,
        private readonly matDialog: MatDialog,
        private readonly projectSettingsService: ProjectConfigurationService,
        private readonly statisticsService: StatisticsService,
    ) {
        this.importanceForm = new FormGroup({});
    }

    ngOnInit(): void {
        // Create the statistics observer
        if (this.sourceDataset !==  null)
        {
            this.statistics$ = timer(0, 2000).pipe(
                switchMap(() => this.statisticsService.fetchStatistics(this.sourceDataset!)),
                filter(data => data !== null),
                take(1),
            );
        } else {
            this.statistics$ = timer(0, 2000).pipe(
                switchMap(() => this.statisticsService.fetchResult()),
                filter(data => data !== null),
                take(1),
            );
        }

        // Prepare observable for metric configuration form
        this.algorithmDefinition$ = this.technicalEvaluationService.algorithms.pipe(
            switchMap(value => {
                return this.technicalEvaluationService.getAlgorithmDefinition(value[0]);
            }),
            tap(value => {
                // Create form
                this.createForm(value);
            }),
            switchMap(value => {
                // Load initial values from backend
                return this.projectSettingsService.projectSettings$.pipe(
                    tap(value1 => {
                        this.importanceForm.patchValue(value1.metricConfiguration);
                    }),
                    map(() => {
                        return value;
                    }),
                );
            }),
        );
    }

    ngOnDestroy(): void {
        if (this.updateSubscription !== null) {
            this.updateSubscription.unsubscribe();
        }
    }

    protected openDialog(templateRef: TemplateRef<any>) {
        this.matDialog.open(templateRef, {
            width: '80%',
        });
    }

    private createForm(algorithmDefinition: AlgorithmDefinition): void {
        const group: any = {};
        this.createGroup(group, algorithmDefinition);
        this.importanceForm = new FormGroup(group);

        if (this.updateSubscription !== null) {
            this.updateSubscription.unsubscribe();
        }
        this.updateSubscription = this.importanceForm.valueChanges.pipe(
            debounceTime(300),
            distinctUntilChanged(),
            switchMap(value => {
                return this.projectSettingsService.projectSettings$.pipe(
                    tap(value1 => {
                       value1.metricConfiguration = value;
                    }),
                );
            }),
            switchMap(value => {
                return this.projectSettingsService.setProjectSettings(value);
            }),
        ).subscribe({
            error: value => {
                // Upload of config failed
                console.error(value);
            }
        });
    }

    private createGroups(formGroup: any, configurations: { [name: string]: ConfigurationGroupDefinition }) {
        Object.values(configurations).forEach((groupDefinition) => {
            this.createGroup(formGroup, groupDefinition);
        });
    }

    private createGroup(group: any, groupDefinition: ConfigurationGroupDefinition): void {
        if (groupDefinition.configurations) {
            this.createGroups(group, groupDefinition.configurations);
        }
        if (groupDefinition.options) {
            Object.keys(groupDefinition.options).forEach(inputDefinition => {
                const validators = [Validators.required];
                group[inputDefinition] = new FormControl({
                    value: MetricImportance.IMPORTANT,
                    disabled: false
                }, validators)
            });
        }
    }

    protected readonly MetricImportanceData = MetricImportanceData;
    protected readonly MetricImportance = MetricImportance;
    protected readonly Object = Object;
}
