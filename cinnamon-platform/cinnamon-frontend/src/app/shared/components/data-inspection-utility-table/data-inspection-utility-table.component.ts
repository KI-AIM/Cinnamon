import { Component, Input, OnInit } from '@angular/core';
import { ProjectSettings } from "@shared/model/project-settings";
import { UtilityData, UtilityStatisticsData } from "@shared/model/statistics";
import { ProjectConfigurationService } from "@shared/services/project-configuration.service";
import { Color, StatisticsService } from "@shared/services/statistics.service";
import { combineLatest, map, Observable } from "rxjs";

@Component({
  selector: 'app-data-inspection-utility-table',
  standalone: false,
  templateUrl: './data-inspection-utility-table.component.html',
  styleUrl: './data-inspection-utility-table.component.less'
})
export class DataInspectionUtilityTableComponent implements OnInit {

    @Input() public predictions!: UtilityData;

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

    protected getFirstElement(obj: UtilityData): Array<UtilityStatisticsData> {
        const keys = Object.keys(obj);
        if (keys.length > 0) {
            return obj[keys[0]];
        }
        return [];
    }

}
