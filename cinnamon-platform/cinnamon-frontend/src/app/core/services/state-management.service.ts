import { HttpClient } from "@angular/common/http";
import { Injectable } from '@angular/core';
import { NavigationEnd, Router } from "@angular/router";
import { ProcessStatus } from "@core/enums/process-status";
import { ExecutionStep, PipelineInformation } from "@shared/model/execution-step";
import { Status } from "@shared/model/status";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { StatusService } from "@shared/services/status.service";
import { UserService } from "@shared/services/user.service";
import { plainToInstance } from "class-transformer";
import { BehaviorSubject, filter, interval, map, Observable, Subscription, switchMap, take, tap } from "rxjs";
import { environments } from "src/environments/environment";
import { StepConfiguration, StepDefinition, Steps } from '../enums/steps';

@Injectable({
    providedIn: 'root'
})
export class StateManagementService {
    /**
     * The step of the page that is currently opened.
     * Null if the current page is not associated with any step, for example, the login page.
     */
    public readonly currentStep$: Observable<StepDefinition | null>;

    private readonly _pipelineSubject: BehaviorSubject<PipelineInformation | null> = new BehaviorSubject<PipelineInformation | null>(null);
    private _pipelineObserver$: Observable<PipelineInformation>;
    private _pipelineSubscription: Subscription | null = null;

    constructor(
        private readonly errorHandlingService: ErrorHandlingService,
        private readonly http: HttpClient,
        private readonly router: Router,
        private readonly userService: UserService,
        private readonly statusService: StatusService
    ) {
        if (this.userService.isAuthenticated()) {
            this.fetchCurrentStep();
        }

        this.currentStep$ = this.router.events.pipe(
            filter(event => event instanceof NavigationEnd),
            map(event => {
               for (const step of Object.values(StepConfiguration)) {
                   if (step.path === event.url) {
                       return step
                   }
               }

               return null;
            }),
        );

        this._pipelineObserver$ = interval(2000).pipe(
            switchMap(() => this.fetchPipelineInformation()),
            tap(value => this.updatePipeline(value))
        );

        this.initPipeline();
    }

    /**
     * Gets an observable for the pipeline information.
     * Emits values in intervalls if the pipeline is running.
     */
    public get pipelineInformation$(): Observable<PipelineInformation> {
        return this._pipelineSubject.asObservable().pipe(
            filter(value => value !=null),
        );
    }

    /**
     * Starts the observer of the pipeline by subscribing.
     * Does nothing if a subscriber already exists.
     * @private
     */
    public startListenToPipeline(): void {
        if (this._pipelineSubscription === null) {
            this._pipelineSubscription = this._pipelineObserver$.subscribe();
        }
    }

    /**
     * Stops listening to the pipeline by unsubscribing.
     * @private
     */
    public stopListenToPipeline(): void {
        if (this._pipelineSubscription !== null) {
            this._pipelineSubscription.unsubscribe();
            this._pipelineSubscription = null;
        }
    }

    /**
     * Updates the subject with the given pipeline.
     * If the stage is running, starts periodical updates.
     * IF the stage is not running, stops the periodical updates.
     *
     * @param pipeline The updated pipeline.
     */
    public updatePipeline(pipeline: PipelineInformation): void {
        this._pipelineSubject.next(pipeline);
        if (pipeline.currentStageIndex == null) {
            this.stopListenToPipeline();
        } else {
            this.startListenToPipeline();
        }
    }

    /**
     * Fetches the pipeline status from the backend and updates the subject.
     */
    public initPipeline(): void {
        this.fetchPipelineInformation().subscribe(value => {
            this.updatePipeline(value);
        });
    }

    /**
     * Updates the given stage of the current pipeline hold by the subject.
     * @param stage The updated stage.
     */
    public updateStage(stage: ExecutionStep): void {
        const current = this._pipelineSubject.value;
        if (current == null) {
            return;
        }

        for (let stageIndex = 0; stageIndex < current.stages.length; stageIndex++) {
            if (current.stages[stageIndex].stageName == stage.stageName) {
                current.stages[stageIndex] = stage;
                this._pipelineSubject.next(current);

                if (stage.status === ProcessStatus.SCHEDULED || stage.status === ProcessStatus.RUNNING) {
                    current.currentStageIndex = stageIndex;
                }

                return;
            }
        }
    }

    /**
     * Fetches the state from the backend.
     */
    public fetchCurrentStep() {
        this.statusService.status$.pipe(
            take(1),
        ).subscribe();
    }

    /**
     * Fetches the state from the backend and routes to the current step.
     */
    public fetchAndRouteToCurrentStep() {
        this.statusService.status$.pipe(
            take(1),
        ).subscribe({
            next: value => {
                this.routeToCurrentStep(value);
            }
        });
    }

    /**
     * Routes to the page for the current step.
     * @param status The current status of the application.
     */
    public routeToCurrentStep(status: Status) {
        for (let [a, b] of Object.entries(StepConfiguration)) {
            if (a === status.currentStep.toString()) {
                this.router.navigateByUrl(b.path);
                return;
            }

        }
        this.router.navigateByUrl("/start");
    }

    /**
     * Fetches the pipeline information from the backend.
     * @return The pipeline information.
     */
    private fetchPipelineInformation(): Observable<PipelineInformation> {
        return this.http.get<PipelineInformation>(environments.apiUrl + "/api/process").pipe(
            map(value => {
                return plainToInstance(PipelineInformation, value);
            }),
        );
    }

    /**
     * Unlocks the given step by resetting subsequent steps.
     * @param unlock The step to unlock.
     */
    public unlockStep(unlock: Steps): void {
        let target;
        if (unlock < Steps.VALIDATION) {
            target = "original";
        } else if (unlock < Steps.EXECUTION) {
            target = "pipeline.execution";
        } else if (unlock < Steps.EVALUATION) {
            target = "pipeline.evaluation";
        } else {
            // Nothing to do
            return;
        }

        const options = {
            params: {target: target},
        };

        this.http.delete(environments.apiUrl + "/api/project/reset", options).pipe(
            switchMap(() => this.statusService.updateNextStep(unlock)),
        ).subscribe({
            error: error => {
                this.errorHandlingService.addError(error);
            }
        });
    }
}
