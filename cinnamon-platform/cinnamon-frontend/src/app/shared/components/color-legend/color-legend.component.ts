import {Component, Input} from '@angular/core';
import {StatisticsService} from "../../services/statistics.service";
import { Observable } from "rxjs";
import { ProjectSettings } from "../../model/project-settings";
import { ProjectConfigurationService } from "../../services/project-configuration.service";

@Component({
    selector: 'app-color-legend',
    templateUrl: './color-legend.component.html',
    styleUrls: ['./color-legend.component.less'],
    standalone: false
})
export class ColorLegendComponent {
    @Input() goodLabel: string = "Similar";
    @Input() badLabel: string = "Different";

    protected projectConfig$: Observable<ProjectSettings>;

    constructor(
        protected readonly projectConfigService: ProjectConfigurationService,
        protected statisticsService: StatisticsService,
    ) {
        this.projectConfig$ = this.projectConfigService.projectSettings$;
    }
}
