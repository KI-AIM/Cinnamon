import { Component, Input, OnInit } from '@angular/core';
import { ProcessStatus } from "@core/enums/process-status";
import { ProjectSettings } from "@shared/model/project-settings";
import { RiskEvaluation, RiskResults } from "@shared/model/risk-evaluation";
import { ProjectConfigurationService } from "@shared/services/project-configuration.service";
import { Color, StatisticsService } from "@shared/services/statistics.service";
import { combineLatest, map, Observable } from "rxjs";

/**
 * Component for displaying results from the risk evaluation.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
  selector: 'app-data-inspection-risks',
  standalone: false,
  templateUrl: './data-inspection-risks.component.html',
  styleUrl: './data-inspection-risks.component.less'
})
export class DataInspectionRisksComponent implements OnInit {

    /**
     * Risk data to be displayed.
     */
    @Input() public risks!: RiskEvaluation;

    protected readonly ProcessStatus = ProcessStatus;

    protected summary: {name: string, results: RiskResults}[];

    protected pageData$: Observable<{
        colorScheme: Color[],
        projectSettings: ProjectSettings,
    }>;

    public constructor(
        private readonly projectConfigService: ProjectConfigurationService,
        private readonly statisticsService: StatisticsService,
    ) {
    }

    public ngOnInit(): void {
        this.summary = [];
        if (this.risks.linkage_health_risk) {
            this.summary.push({
                name: "Linkage",
                results: this.risks.linkage_health_risk,
            });
        }
        if (this.risks.univariate_singling_out_risk) {
            this.summary.push({
                name: "Singling-out univariate",
                results: this.risks.univariate_singling_out_risk,
            });
        }
        if (this.risks.multivariate_singling_out_risk) {
            this.summary.push({
                name: "Singling-out multivariate",
                results: this.risks.multivariate_singling_out_risk,
            });
        }

        this.pageData$ = combineLatest({
            projectSettings: this.projectConfigService.projectSettings$,
        }).pipe(
            map(value => {
                return {
                    ...value,
                    colorScheme: this.statisticsService.getColorScheme(value.projectSettings.metricConfiguration.colorScheme),
                }
            })
        );
    }
}
