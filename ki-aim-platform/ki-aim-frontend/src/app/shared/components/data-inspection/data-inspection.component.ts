import { Component, Input, OnInit } from '@angular/core';
import { Observable  } from "rxjs";
import { Statistics } from "../../model/statistics";
import {StatisticsService} from "../../services/statistics.service";

@Component({
    selector: 'app-data-inspection',
    templateUrl: './data-inspection.component.html',
    styleUrls: ['./data-inspection.component.less']
})
export class DataInspectionComponent implements OnInit {
    @Input() public step!: string;

    protected statistics$: Observable<Statistics>;

    protected filterText: string;

    constructor(
        private readonly statisticsService: StatisticsService,
    ) {
    }

    ngOnInit(): void {
        this.statistics$ = this.statisticsService.statistics$;
    }
}
