import { Component, Input, OnInit } from '@angular/core';
import { ProcessStatus } from "@core/enums/process-status";
import { ProjectSettings } from "@shared/model/project-settings";
import { RiskEvaluation } from "@shared/model/risk-evaluation";
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
