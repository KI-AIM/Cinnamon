import { Component, OnInit } from '@angular/core';
import { EvaluationService } from "../../services/evaluation.service";
import { TitleService } from "../../../../core/services/title-service.service";
import { ProcessStatus } from "../../../../core/enums/process-status";
import { Observable, tap } from "rxjs";
import { StatisticsService } from "../../../../shared/services/statistics.service";
import {
    StatisticsResponse, UtilityData,
    UtilityMetricData2,
    UtilityMetricData3,
    UtilityStatisticsData
} from "../../../../shared/model/statistics";
import { ExecutionStep } from "../../../../shared/model/execution-step";
import { RiskEvaluation } from '../../../../shared/model/risk-evaluation';
import { ProjectConfigurationService } from "../../../../shared/services/project-configuration.service";
import { ProjectSettings } from "../../../../shared/model/project-settings";
import { StageDefinition } from "../../../../shared/services/execution-step.service";

@Component({
    selector: 'app-evaluation',
    templateUrl: './evaluation.component.html',
    styleUrls: ['./evaluation.component.less'],
    standalone: false
})
export class EvaluationComponent implements OnInit {
    protected readonly ProcessStatus = ProcessStatus;
    protected readonly UtilityData = UtilityData;
    protected readonly UtilityMetricData2 = UtilityMetricData2;
    protected readonly UtilityMetricData3 = UtilityMetricData3;

    protected stage$: Observable<ExecutionStep | null>;
    protected stageDefinition$: Observable<StageDefinition>;

    protected statistics$: Observable<StatisticsResponse>;

    protected risks$: Observable<RiskEvaluation>;
    protected risks2$: Observable<any>;

    protected riskMetrics: {
        label: string,
        key: keyof RiskEvaluation,
        hasAttackRate: boolean
    }[] = [
        { label: "Linkage", key: "linkage_health_risk", hasAttackRate: true },
        // { label: "Singling-out univariate", key: "univariate_singling_out_risk", hasAttackRate: true },
        // { label: "Singling-out multivariate", key: "multivariate_singling_out_risk", hasAttackRate: true },
        // { label: "Average Attribute Inference", key: "inference_average_risk", hasAttackRate: true }
    ];

    protected projectConfig$: Observable<ProjectSettings>;

    constructor(
        protected readonly evaluationService: EvaluationService,
        protected readonly statisticsService: StatisticsService,
        private readonly titleService: TitleService,
        protected readonly projectConfigService: ProjectConfigurationService,
    ) {
        this.titleService.setPageTitle("Evaluation");
    }

    ngOnInit() {
        this.projectConfig$ = this.projectConfigService.projectSettings$;

        this.risks$ = this.statisticsService.fetchRisks().pipe(
            tap(risks => console.log("Risks data:", risks)) // Debug
        );
        this.risks2$ = this.statisticsService.fetchRisks2();
        this.stage$ = this.evaluationService.status$;
        this.stageDefinition$ = this.evaluationService.fetchStageDefinition$();
        this.statistics$ = this.statisticsService.fetchResult();
        this.evaluationService.fetchStatus();
    }

    // Method for formatting the confidence interval
    formatConfidenceInterval(interval?: [number, number]): string {
        if (!interval || interval.length !== 2) {
            return 'N/A';
        }
        return `[${interval[0].toFixed(2)}, ${interval[1].toFixed(2)}]`;
    }

    protected getFirstElement(obj: UtilityData): Array<UtilityStatisticsData> {
        const keys = Object.keys(obj);
        if (keys.length > 0) {
            return obj[keys[0]];
        }
        return [];
    }

    protected getJobName(name: string): string {
        const jobNames: Record<string, string> = {
            'technical_evaluation': 'Technical Evaluation',
            'risk_evaluation': 'Risk Evaluation',
            'base_evaluation': 'Base Evaluation',
        };

        return jobNames[name];
    }

    // Method for generatting color index
    generateRiskColorIndex(riskValue?: number): number {
        if (riskValue === undefined) return 0;
        return Math.min(Math.floor(riskValue * 10), 9)+1;
    }
}
