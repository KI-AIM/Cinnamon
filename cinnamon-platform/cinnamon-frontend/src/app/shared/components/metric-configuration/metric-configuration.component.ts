import { Component, OnDestroy, OnInit, TemplateRef, ViewChild } from '@angular/core';
import {
    catchError,
    debounceTime,
    distinctUntilChanged,
    map,
    Observable,
    of,
    Subscription,
    switchMap,
    tap
} from "rxjs";
import { AlgorithmDefinition } from "../../model/algorithm-definition";
import {
    TechnicalEvaluationService
} from "../../../features/technical-evaluation/services/technical-evaluation.service";
import { ProjectConfigurationService } from "../../services/project-configuration.service";
import { FormControl, FormGroup, Validators } from "@angular/forms";
import { ConfigurationGroupDefinition } from "../../model/configuration-group-definition";
import { MetricImportance, MetricImportanceData } from "../../model/project-settings";
import { MatDialog } from "@angular/material/dialog";
import { StatisticsService } from "../../services/statistics.service";
import { ErrorHandlingService } from "../../services/error-handling.service";

@Component({
    selector: 'app-metric-configuration',
    templateUrl: './metric-configuration.component.html',
    styleUrls: ['./metric-configuration.component.less'],
    standalone: false
})
export class MetricConfigurationComponent implements OnInit, OnDestroy {
    protected readonly MetricImportance = MetricImportance;
    protected readonly MetricImportanceData = MetricImportanceData;
    protected readonly Object = Object;

    protected algorithmDefinition$: Observable<AlgorithmDefinition>;
    protected importanceForm: FormGroup;

    private updateSubscription: Subscription | null = null;
    @ViewChild('metricSelectionDialog') private dialogWrap: TemplateRef<any>;

    constructor(
        private readonly errorHandlingService: ErrorHandlingService,
        private readonly matDialog: MatDialog,
        private readonly projectConfigurationService: ProjectConfigurationService,
        protected readonly statisticsService: StatisticsService,
        private readonly technicalEvaluationService: TechnicalEvaluationService,
    ) {
        this.importanceForm = new FormGroup({});
    }

    public ngOnInit(): void {
        // Prepare observable for metric configuration form
        this.algorithmDefinition$ = this.technicalEvaluationService.algorithms.pipe(
            switchMap(value => {
                return this.technicalEvaluationService.getAlgorithmDefinition(value[0]);
            }),
            catchError(error => {
                this.errorHandlingService.addError(error, "Failed to load metrics!");
                return of(new AlgorithmDefinition());
            }),
            tap(value => {
                // Create form
                this.createForm(value);
            }),
            switchMap(value => {
                // Load initial values from backend
                return this.projectConfigurationService.projectSettings$.pipe(
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

    public ngOnDestroy(): void {
        if (this.updateSubscription !== null) {
            this.updateSubscription.unsubscribe();
        }
    }

    public open(): void {
        this.matDialog.open(this.dialogWrap, {
            width: '60%'
        });
    }

    private createForm(algorithmDefinition: AlgorithmDefinition): void {
        const form: any = {};

        form["colorScheme"] = new FormControl({value: 'Default', disabled: false});

        form["useUserDefinedImportance"] = new FormControl({value: false, disabled: false});

        const group: any = {};
        this.createGroup(group, algorithmDefinition);
        form["userDefinedImportance"] = new FormGroup(group);

        this.importanceForm = new FormGroup(form);

        if (this.updateSubscription !== null) {
            this.updateSubscription.unsubscribe();
        }
        this.updateSubscription = this.importanceForm.valueChanges.pipe(
            debounceTime(300),
            distinctUntilChanged(),
            switchMap(value => {
                return this.projectConfigurationService.projectSettings$.pipe(
                    tap(value1 => {
                        value1.metricConfiguration = value;
                    }),
                );
            }),
            switchMap(value => {
                return this.projectConfigurationService.setProjectSettings(value);
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
}
