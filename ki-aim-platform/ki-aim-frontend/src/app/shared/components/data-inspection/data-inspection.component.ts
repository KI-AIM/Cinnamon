import {Component, Input, OnInit, TemplateRef} from '@angular/core';
import {filter, Observable, switchMap, take, tap, timer} from "rxjs";
import { Statistics } from "../../model/statistics";
import {StatisticsService} from "../../services/statistics.service";
import {Steps} from "../../../core/enums/steps";
import {MatDialog} from "@angular/material/dialog";
import {AlgorithmDefinition} from "../../model/algorithm-definition";
import {TechnicalEvaluationService} from "../../../features/technical-evaluation/services/technical-evaluation.service";
import {FormControl, FormGroup, Validators} from "@angular/forms";
import {ConfigurationGroupDefinition} from "../../model/configuration-group-definition";

@Component({
    selector: 'app-data-inspection',
    templateUrl: './data-inspection.component.html',
    styleUrls: ['./data-inspection.component.less']
})
export class DataInspectionComponent implements OnInit {
    @Input() public sourceDataset: string | null = null;
    @Input() public sourceProcess: string | null = null;
    @Input() public mainData: 'real' | 'synthetic' = 'real';
    @Input() public processingSteps: Steps[] = [];

    protected statistics$: Observable<Statistics | null>;

    protected filterText: string;

    protected algorithmDefinition$: Observable<AlgorithmDefinition>;
    protected importanceForm: FormGroup;

    constructor(
        private readonly tes: TechnicalEvaluationService,
        private readonly matDialog: MatDialog,
        private readonly statisticsService: StatisticsService,
    ) {
        this.importanceForm = new FormGroup({});
    }

    ngOnInit(): void {
        // Create the statistics observer
        if (this.sourceDataset === 'VALIDATION') {
            this.statistics$ = timer(0, 2000).pipe(
                switchMap(() => this.statisticsService.statistics$),
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

        this.algorithmDefinition$ = this.tes.algorithms.pipe(
            switchMap(value => {
                return this.tes.getAlgorithmDefinition(value[0]);
            }),
            tap(value => {
                this.createForm(value);
            }),
        );
    }

    protected openDialog(templateRef: TemplateRef<any>) {
        this.matDialog.open(templateRef, {
            width: '80%',
        });
    }

    protected readonly MetricImportance = MetricImportance;
    protected readonly Object = Object;

    protected readonly MetricImportanceLabels: {[key in MetricImportance]: {label: string}} = {
        IMPORTANT : {
            label: 'Important',
        },
        ADDITIONAL: {
            label: 'Additional',
        },
        NOT_RELEVANT: {
            label: 'Not Relevant',
        }
    }

    private createForm(algorithmDefinition: AlgorithmDefinition): void {
        const formGroup: any = {};
        if (algorithmDefinition.configurations) {
            this.createGroups(formGroup, algorithmDefinition.configurations);
        }
        if (algorithmDefinition.options) {
            Object.keys(algorithmDefinition.options).forEach(inputDefinition => {
                // Add validators of the input
                const validators = [Validators.required];
                formGroup[inputDefinition] = new FormControl({}, validators)
            });
            // this.createGroups(formGroup, algorithmDefinition.options);
        }
        this.importanceForm = new FormGroup(formGroup);
    }

    private createGroups(formGroup: any, configurations: { [name: string]: ConfigurationGroupDefinition }) {
        Object.entries(configurations).forEach(([name, groupDefinition]) => {
            formGroup[name] = this.createGroup(groupDefinition);
        });
    }

    private createGroup(groupDefinition: ConfigurationGroupDefinition): FormGroup {
        const group: any = {};

        if (groupDefinition.parameters) {
            groupDefinition.parameters.forEach(inputDefinition => {
                // Add validators of the input
                const validators = [Validators.required];
                group[inputDefinition.name] = new FormControl({}, validators)
            });
        }

        if (groupDefinition.configurations) {
            this.createGroups(group, groupDefinition.configurations);
        }
        if (groupDefinition.options) {
            this.createGroups(group, groupDefinition.options);
        }

        return new FormGroup(group);
    }

}

enum MetricImportance {
    IMPORTANT = "IMPORTANT",
    ADDITIONAL = "ADDITIONAL",
    NOT_RELEVANT = "NOT_RELEVANT",
}
