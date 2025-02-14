import {Component, Input} from '@angular/core';
import {StatisticsService} from "../../services/statistics.service";

@Component({
  selector: 'app-color-legend',
  templateUrl: './color-legend.component.html',
  styleUrls: ['./color-legend.component.less']
})
export class ColorLegendComponent {
    @Input() goodLabel: string = "Similar";
    @Input() badLabel: string = "Different";

    constructor(
        protected statisticsService: StatisticsService,
    ) {
    }
}
