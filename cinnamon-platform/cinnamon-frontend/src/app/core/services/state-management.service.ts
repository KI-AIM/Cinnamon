import { HttpClient } from "@angular/common/http";
import { Injectable } from '@angular/core';
import { NavigationEnd, Router } from "@angular/router";
import { ProcessStatus } from "@core/enums/process-status";
import { DataSetInfoService } from "@features/data-upload/services/data-set-info.service";
import { FileService } from "@features/data-upload/services/file.service";
import { ExecutionStep, PipelineInformation } from "@shared/model/execution-step";
import { Status } from "@shared/model/status";
import { ConfigurationService } from "@shared/services/configuration.service";
import { StatusService } from "@shared/services/status.service";
import { UserService } from "@shared/services/user.service";
import { plainToInstance } from "class-transformer";
import {
    BehaviorSubject,
    filter,
    interval,
    map,
    Observable,
    of,
    shareReplay,
    Subscription,
    switchMap,
    take,
    tap
} from "rxjs";
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
        private readonly configurationService: ConfigurationService,
        protected readonly dataSetInfoService: DataSetInfoService,
        private readonly fileService: FileService,
        private readonly http: HttpClient,
        private readonly router: Router,
        private readonly userService: UserService,
        private readonly statusService: StatusService,
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
            shareReplay(1),
        );

        this._pipelineObserver$ = interval(2000).pipe(
            switchMap(() => this.fetchPipelineInformation()),
            tap(value => this.updatePipeline(value)),
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
     * Observable that updates if the current page should be locked.
     */
    public get currentStepLocked$(): Observable<LockedInformation> {
        return of(null).pipe(
            switchMap(() => {
                return this.currentStep$;
            }),
            switchMap(value => {
                return this.pipelineInformation$.pipe(
                    map(p => {
                        return {currentStep: value, p: p};
                    }),
                );
            }),
            switchMap(value => {
                return this.statusService.status$.pipe(
                    map(status => {
                        return {currentStep: value.currentStep, p: value.p, status: status};
                    }),
                );
            }),
            map(value =>{
                const reasons: LockedReason[] = [];
                if (value.currentStep != null && value.currentStep.enum != Steps.WELCOME) {
                    if (StepConfiguration[value.currentStep.enum].index <= StepConfiguration[Steps.VALIDATION].index &&
                        this.statusService.isStepCompleted(value.currentStep.lockedAfter)) {
                        reasons.push(LockedReason.STEP_CONFIRMED);
                    }
                    if (value.p.currentStageIndex != null && value.currentStep.enum !== value.status.currentStep) {
                        reasons.push(LockedReason.PROCESS_RUNNING);
                    }
                    if (value.currentStep.stageName != null) {
                        // Check if all previous stages are finished
                         for (const stage of value.p.stages) {
                             if (stage.stageName === value.currentStep.stageName) {
                                 break;
                             } else {
                                 if (stage.status !== ProcessStatus.FINISHED) {
                                     reasons.push(LockedReason.PREVIOUS_STAGE_NOT_FINISHED);
                                     break;
                                 }
                             }
                         }
                    }
                }
                return {isLocked: reasons.length > 0, reasons: reasons, currentStep: value.currentStep ?? null} as LockedInformation;
            }),
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
    public unlockStep(unlock: Steps): Observable<void> {
        const index = StepConfiguration[unlock].index;

        let target;
        if (index <= StepConfiguration[Steps.VALIDATION].index) {
            target = "original";
        } else if (index <= StepConfiguration[Steps.EXECUTION].index) {
            target = "pipeline.execution";
        } else if (index <= StepConfiguration[Steps.EVALUATION].index) {
            target = "pipeline.evaluation";
        } else {
            // Nothing to do
            return of(undefined);
        }

        const options = {
            params: {target: target},
        };

        return this.http.delete<void>(environments.apiUrl + "/api/project/reset", options).pipe(
            tap(() => {
                this.initPipeline();
                this.configurationService.invalidateCache();
                this.fileService.invalidateCache();
                this.dataSetInfoService.invalidateCache();
            }),
        );
    }
}

/**
 * Information for displaying the info box about locked steps
 */
export interface LockedInformation {
    /**
     * If the current step is locked.
     */
    isLocked: boolean;
    /**
     * Reasons for the state being locked.
     */
    reasons: LockedReason[];
    /**
     * The step of the visited page.
     * Value is null if the page is not part of a step.
     */
    currentStep: StepDefinition | null;
}

/**
 * Reasons for a step being locked.
 */
export enum LockedReason {
    /**
     * Locked data configuration because of a previous confirmation of the data.
     */
    STEP_CONFIRMED = "STEP_CONFIRMED",
    /**
     * Locked because of a running process.
     */
    PROCESS_RUNNING = "PROCESS_RUNNING",
    /**
     * Locked because of an incomplete or outdated previous stage.
     */
    PREVIOUS_STAGE_NOT_FINISHED = "PREVIOUS_STAGE_NOT_FINISHED",
}
