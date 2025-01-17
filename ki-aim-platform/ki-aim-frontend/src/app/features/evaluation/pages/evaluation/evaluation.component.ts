import {Component, OnInit} from '@angular/core';
import {EvaluationService} from "../../services/evaluation.service";
import {TitleService} from "../../../../core/services/title-service.service";
import {ProcessStatus} from "../../../../core/enums/process-status";
import {environments} from "../../../../../environments/environment";
import {HttpClient} from "@angular/common/http";
import {map, Observable, of, tap} from "rxjs";
import {StatisticsService} from "../../../../shared/services/statistics.service";
import {Statistics} from "../../../../shared/model/statistics";
import {plainToInstance} from "class-transformer";
import {ExecutionStep} from "../../../../shared/model/execution-step";

@Component({
    selector: 'app-evaluation',
    templateUrl: './evaluation.component.html',
    styleUrls: ['./evaluation.component.less'],
})
export class EvaluationComponent implements OnInit {
    protected readonly ProcessStatus = ProcessStatus;

    protected stage$: Observable<ExecutionStep | null>;

    private _result: Statistics | null = null;

    constructor(
        protected readonly evaluationService: EvaluationService,
        protected readonly statisticsService: StatisticsService,
        private readonly http: HttpClient,
        private readonly titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Evaluation");
    }

    ngOnInit() {
        this.stage$ = this.evaluationService.status$;
        this.evaluationService.fetchStatus();
    }

    protected getResult(): Observable<Statistics> {
        if (this._result !== null) {
            return of(this._result);
        } else {
            return this.fetchResult().pipe(
                tap(value => {
                    this._result = value;
                }),
            );
        }
    }

    protected getReal(obj: any) {
        return obj['real'];
    }

    protected getSyntheticAttribute(obj: any, attribute: any) {
        return obj['synthetic'][attribute];
    }

    protected getValues(obj: any): any {
        return obj.value;
    }

    protected isObject(abc: any): boolean {
        return typeof abc === "object";
    }

    protected isNormalTable(name: any): boolean {
        return ['distinct_values', 'fifth_percentile', 'kurtosis', 'maximum', 'mean', 'median', 'minimum', 'missing_values_count', 'missing_values_percentage', 'mode', 'ninety_fifth_percentile', 'q1', 'q3', 'skewness', 'standard_deviation', 'variance'].includes(name);
    }

    protected getHellingerDistance(blub: any): string {
        if (!blub['hellinger_distance']) {
            return 'N/A';
        }
        return blub['hellinger_distance']['value'];
    }


    /**
     * Downloads all files related to the project in a ZIP file.
     * @protected
     */
    protected downloadResult() {
        this.http.get(environments.apiUrl + "/api/project/zip", {responseType: 'arraybuffer'}).subscribe({
            next: data => {
                const blob = new Blob([data], {
                    type: 'application/zip'
                });
                const url = window.URL.createObjectURL(blob);
                window.open(url);
            }
        });
    }

    protected readonly JSON = JSON;
    protected readonly Object = Object;

    private fetchResult(): Observable<Statistics> {
        return this.http.get<string>(environments.apiUrl + `/api/project/resultFile`,
            {
                params: {
                    executionStepName: 'EVALUATION',
                    processStepName: 'TECHNICAL_EVALUATION',
                    name: 'metrics.json',
                }
            }).pipe(
                map(value => plainToInstance(Statistics, JSON.parse(value)))
        );
    }
}
