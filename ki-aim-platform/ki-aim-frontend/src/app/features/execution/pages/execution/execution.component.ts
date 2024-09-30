import { Component, OnDestroy, OnInit } from '@angular/core';
import { ProcessStatus } from "../../../../core/enums/process-status";
import { environments } from "../../../../../environments/environment";
import { HttpClient } from "@angular/common/http";
import { ExecutionStep } from "../../../../shared/model/execution-step";
import { TitleService } from "../../../../core/services/title-service.service";
import {StateManagementService} from "../../../../core/services/state-management.service";
import { Steps } from "../../../../core/enums/steps";
import { Router } from "@angular/router";
import { ExecutionService } from "../../services/execution.service";

@Component({
  selector: 'app-execution',
  templateUrl: './execution.component.html',
  styleUrls: ['./execution.component.less'],
})
export class ExecutionComponent implements OnInit, OnDestroy {
    protected readonly ProcessStatus = ProcessStatus;

    constructor(
        protected readonly executionService: ExecutionService,
        private readonly http: HttpClient,
        private readonly router: Router,
        readonly stateManagementService: StateManagementService,
        private readonly titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Execution");
    }

    ngOnDestroy() {
        this.executionService.stopListenToStatus();
    }

    ngOnInit() {
        this.executionService.fetchStatus();
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
                this.stateManagementService.setNextStep(Steps.TECHNICAL_EVALUATION);
            },
            error: (e) =>{
                console.error(e);
            }
        });
    }
}
