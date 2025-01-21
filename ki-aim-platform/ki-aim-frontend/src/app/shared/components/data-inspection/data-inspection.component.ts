import { Component, Input, OnInit } from '@angular/core';
import { filter, Observable, switchMap, take, timer } from "rxjs";
import { Statistics } from "../../model/statistics";
import { StatisticsService } from "../../services/statistics.service";
import { Steps } from "../../../core/enums/steps";

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

    protected readonly Object = Object;

    protected filterText: string;
    protected statistics$: Observable<Statistics | null>;

    constructor(
        private readonly statisticsService: StatisticsService,
    ) {
    }

    ngOnInit(): void {
        // Create the statistics observer
        if (this.sourceDataset !==  null)
        {
            this.statistics$ = timer(0, 2000).pipe(
                switchMap(() => this.statisticsService.fetchStatistics(this.sourceDataset!)),
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
    }

}
