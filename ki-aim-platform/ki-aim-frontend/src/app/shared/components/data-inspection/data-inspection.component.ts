import { Component, Input, OnInit } from '@angular/core';
import {filter, interval, Observable, switchMap, take} from "rxjs";
import { Statistics } from "../../model/statistics";
import {StatisticsService} from "../../services/statistics.service";

@Component({
    selector: 'app-data-inspection',
    templateUrl: './data-inspection.component.html',
    styleUrls: ['./data-inspection.component.less']
})
export class DataInspectionComponent implements OnInit {
    @Input() public sourceDataset: string | null = null;
    @Input() public sourceProcess: string | null = null;

    protected statistics$: Observable<Statistics>;

    protected filterText: string;

    constructor(
        private readonly statisticsService: StatisticsService,
    ) {
    }

    ngOnInit(): void {
        // Create the statistics observer
        if (this.sourceDataset === 'VALIDATION') {
            this.statistics$ = interval(2000).pipe(
                switchMap(() => this.statisticsService.statistics$),
                filter(data => data !== null),
                take(1),
            );
        } else {
            this.statistics$ = interval(2000).pipe(
                switchMap(() => this.statisticsService.fetchResult()),
                filter(data => data !== null),
                take(1),
            );
        }
    }

}
