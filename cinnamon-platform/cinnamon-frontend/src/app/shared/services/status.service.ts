import { HttpClient } from "@angular/common/http";
import { Injectable, OnDestroy } from '@angular/core';
import { Mode } from "@core/enums/mode";
import { StepConfiguration, Steps } from "@core/enums/steps";
import { List } from "@core/utils/list";
import { Status } from "@shared/model/status";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { ProjectService } from "@shared/services/project.service";
import { UserService } from "@shared/services/user.service";
import { BehaviorSubject, filter, Observable, of, Subscription } from "rxjs";
import { environments } from "src/environments/environment";

@Injectable({
    providedIn: 'root'
})
export class StatusService implements OnDestroy {
    private readonly baseUrl: string = environments.apiUrl + "/api/project"

    private statusSubject: BehaviorSubject<Status | null> = new BehaviorSubject<Status | null>(null);

    private _projectOpenSubscription: Subscription;
    private _projectClosedSubscription: Subscription;


    /**
     * List of all completed steps.
     * @private
     */
    private readonly completedSteps: List<Steps> = new List();

    constructor(
        private readonly errorHandlingService: ErrorHandlingService,
        private readonly http: HttpClient,
        readonly userService: UserService,
        readonly projectService: ProjectService,
    ) {
        this._projectOpenSubscription = projectService.projectOpen$.subscribe({
            next: (value) => this.updateStatus(value.id),
        });

        this._projectClosedSubscription = projectService.projectClosed.subscribe({
            next: () => this.statusSubject.next(null),
        });

        // if (this.projectService.project) {
        //     this.updateStatus(this.projectService.project.id);
        // }
    }

    public ngOnDestroy(): void {
        this._projectOpenSubscription?.unsubscribe();
        this._projectClosedSubscription?.unsubscribe();
    }

    /**
     * Creates an observable that emits the current project status.
     * If no project is available, it will emit null.
     *
     * @returns An observable that emits the current status.
     */
    public get status$(): Observable<Status | null> {
        return this.statusSubject.asObservable();
    }

    /**
     * Creates an observable that emits the current project status.
     * Does not emit null.
     *
     * @returns An observable that emits the current status.
     */
    public get statusNonNull$(): Observable<Status> {
        return this.statusSubject.asObservable().pipe(
            filter((status): status is Status => status !== null),
        );
    }

    /**
     * Sets the mode to the given value and synchronizes the status with the backend.
     * @param mode The selected mode.
     */
    public setMode(mode: Mode): Observable<void> {
        const currentStatus = this.statusSubject.value;
        if (currentStatus == null) {
            return of();
        }

        currentStatus.mode = mode;
        this.statusSubject.next(currentStatus);

        const formData = new FormData();
        formData.append("mode", mode.toString());
        return this.http.post<void>(this.baseUrl, formData);
    }

    getCompletedSteps(): List<Object> {
        return this.completedSteps;
    }

    /**
     * Sets the given step to the current steps, marks all previous steps as completed, and updates the backend.
     * Steps after the given step will be removed from the list of completed steps.
     *
     * @param step
     */
    public updateNextStep(step: Steps): Observable<void> {
        this.setNextStep(step);
        return this.postStep(step);
    }

    /**
     * Sets the given step to the current steps and marks all previous steps as completed.
     * Steps after the given step will be removed from the list of completed steps.
     *
     * @param step The next step.
     */
    private setNextStep(step: Steps) {
        const currentStatus = this.statusSubject.value;
        if (currentStatus == null) {
            return;
        }

        currentStatus.currentStep = step;
        this.setCompletedSteps(step);
        this.statusSubject.next(currentStatus);
    }

    /**
     * Marks all steps before the given step as completed and removes all steps after the given step from the list of completed steps.
     * @param step The step to mark as completed.
     */
    private setCompletedSteps(step: Steps) {
        this.completedSteps.clear();

        const currentIndex = StepConfiguration[step].index;
        Object.values(StepConfiguration).forEach(value => {
            if (value.index < currentIndex) {
                this.addCompletedStep(value.enum);
            }
        });
    }

    private postStep(step: Steps): Observable<void> {
        const formData = new FormData();
        formData.append("step", step);
        return this.http.post<void>(this.baseUrl + "/step", formData);
    }

    /**
     * Fetches the current status from the backend and updates the status subject.
     */
    public updateStatus(projectId: string) {
        this.http.get<Status>(this.baseUrl + "/" + projectId + "/status").subscribe({
            next: (value: Status) => {
                this.setCompletedSteps(value.currentStep);
                this.statusSubject.next(value);
            },
            error: err => {
                this.errorHandlingService.addError(err, "Failed to fetch project state.");
            },
        });
    }

    addCompletedStep(step: Steps): void {
        if (!this.completedSteps.contains(step)) {
            this.completedSteps.add(step);
        }
    }

    /**
     * Checks if a given step is completed.
     * If the step is null or undefined, returns false.
     *
     * @param step The step to check.
     * @returns If the given step is completed.
     */
    public isStepCompleted(step: Steps | null | undefined) {
        if (step == null) {
            return false;
        }
        return this.completedSteps.contains(step);
    }
}
