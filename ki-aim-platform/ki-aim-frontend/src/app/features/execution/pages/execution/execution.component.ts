import { Component, OnDestroy, OnInit } from '@angular/core';
import { ProcessStatus } from "../../../../core/enums/process-status";
import { environments } from "../../../../../environments/environment";
import { HttpClient } from "@angular/common/http";
import { ExecutionStep } from "../../../../shared/model/execution-step";
import { TitleService } from "../../../../core/services/title-service.service";
import { Steps } from "../../../../core/enums/steps";
import { Router } from "@angular/router";
import { ExecutionService } from "../../services/execution.service";
import { StatusService } from "../../../../shared/services/status.service";
import {Observable} from "rxjs";
import {StageConfiguration} from "../../../../shared/model/stage-configuration";

@Component({
  selector: 'app-execution',
  templateUrl: './execution.component.html',
  styleUrls: ['./execution.component.less'],
})
export class ExecutionComponent implements OnInit, OnDestroy {
    protected readonly ProcessStatus = ProcessStatus;
    protected stageConfiguration$: Observable<StageConfiguration>;

    constructor(
        protected readonly executionService: ExecutionService,
        private readonly http: HttpClient,
        private readonly router: Router,
        private readonly statusService: StatusService,
        private readonly titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Execution");
    }

    ngOnDestroy() {
        this.executionService.stopListenToStatus();
    }

    ngOnInit() {
        this.executionService.fetchStatus();
        this.stageConfiguration$ = this.executionService.fetchStageConfiguration();
    }

    protected get status(): ExecutionStep {
        return this.executionService.status;
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

    protected continue() {
        this.http.post(environments.apiUrl + "/api/process/confirm", {}).subscribe({
            next: () => {
                this.router.navigateByUrl("/technicalEvaluationConfiguration");
                this.statusService.setNextStep(Steps.TECHNICAL_EVALUATION);
            },
            error: (e) =>{
                console.error(e);
            }
        });
    }

    protected isObject(value: string | object) {
        return typeof value === 'object';
    }

    protected castObject(value: any): any {
        return value as any;
    }

    protected getJobName(index: number): string {
        return ["Anonymization", "Synthetization"][index];
    }
}
