import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import {
    catchError,
    distinctUntilKeyChanged,
    filter,
    Observable,
    of,
    ReplaySubject,
    Subject,
    switchMap,
    take,
    takeUntil,
    tap,
    timer
} from "rxjs";
import { StatisticsResponse } from "../../model/statistics";
import { StatisticsService } from "../../services/statistics.service";
import { ErrorHandlingService } from "../../services/error-handling.service";
import { ProcessStatus } from "../../../core/enums/process-status";

@Component({
    selector: 'app-data-inspection',
    templateUrl: './data-inspection.component.html',
    styleUrls: ['./data-inspection.component.less'],
    standalone: false
})
export class DataInspectionComponent implements OnInit, OnDestroy {
    @Input() public sourceDataset: string | null = null;
    @Input() public sourceProcess: string | null = null;
    @Input() public mainData: 'real' | 'synthetic' = 'real';
    @Input() public processingSteps: string[] = [];

    protected readonly ProcessStatus = ProcessStatus;

    protected filterText: string;
    protected statistics$: Observable<StatisticsResponse | null>;

    private statisticsSubject = new Subject<StatisticsResponse>();

    private cancelSubject = new Subject<void>();
    private reloadSubject = new ReplaySubject<void>();

    constructor(
        private readonly  errorHandlingService: ErrorHandlingService,
        private readonly statisticsService: StatisticsService,
    ) {
    }

    ngOnInit(): void {
        // Create the statistics observer
        if (this.sourceDataset !==  null)
        {
            this.reloadSubject.pipe(
                switchMap(() => {
                    return timer(0, 2000).pipe(
                        takeUntil(this.cancelSubject),
                        switchMap(() => this.statisticsService.fetchStatistics(this.sourceDataset!)),
                        distinctUntilKeyChanged('status'),
                        tap(value => this.statisticsSubject.next(value)),
                        filter(data => data.status !== ProcessStatus.RUNNING && data.status !== ProcessStatus.SCHEDULED),
                        take(1),
                        catchError(() => {
                            this.errorHandlingService.addError("Failed to calculate statistics!");
                            return of(new StatisticsResponse(ProcessStatus.ERROR));
                        }),
                    );
                }),
            ).subscribe();
            this.statistics$ = this.statisticsSubject.asObservable();

            // Start pipeline
            this.reload();
        } else {
            this.statistics$ = timer(0, 2000).pipe(
                switchMap(() => this.statisticsService.fetchResult()),
                filter(data => data.status === ProcessStatus.FINISHED),
                take(1),
                catchError(() => {
                    this.errorHandlingService.addError("Failed to calculate statistics!");
                    return of(null);
                }),
            );
        }
    }

    ngOnDestroy(): void {
        this.cancel();
    }

    /**
     * Cancels the statistics calculation.
     * @protected
     */
    protected cancel() {
        if (this.sourceDataset !== null) {
            this.statisticsService.cancelStatistics(this.sourceDataset).subscribe({
                next: () => {
                    this.cancelSubject.next();
                    this.statisticsSubject.next(new StatisticsResponse(ProcessStatus.CANCELED));
                },
                error: () => {
                    this.errorHandlingService.addError("Failed to cancel statistics calculation!");
                }
            });
        }
    }

    /**
     * Reloads the statistics.
     * @protected
     */
    protected reload() {
        this.reloadSubject.next();
        this.statisticsSubject.next(new StatisticsResponse(ProcessStatus.RUNNING));
    }
}
