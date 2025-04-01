import { Component, Input, OnInit } from '@angular/core';
import { catchError, filter, Observable, of, switchMap, take, timer } from "rxjs";
import { Statistics } from "../../model/statistics";
import { StatisticsService } from "../../services/statistics.service";
import { ErrorHandlingService } from "../../services/error-handling.service";

@Component({
    selector: 'app-data-inspection',
    templateUrl: './data-inspection.component.html',
    styleUrls: ['./data-inspection.component.less'],
    standalone: false
})
export class DataInspectionComponent implements OnInit {
    @Input() public sourceDataset: string | null = null;
    @Input() public sourceProcess: string | null = null;
    @Input() public mainData: 'real' | 'synthetic' = 'real';
    @Input() public processingSteps: string[] = [];

    protected readonly Object = Object;

    protected filterText: string;
    protected statistics$: Observable<Statistics | null>;

    constructor(
        private readonly  errorHandlingService: ErrorHandlingService,
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
                catchError(err => {
                    this.errorHandlingService.addError("Failed to calculate statistics!");
                    return of(null);
                }),
            );
        } else {
            this.statistics$ = timer(0, 2000).pipe(
                switchMap(() => this.statisticsService.fetchResult()),
                filter(data => data !== null),
                take(1),
                catchError(err => {
                    this.errorHandlingService.addError("Failed to calculate statistics!");
                    return of(null);
                }),
            );
        }
    }

}
