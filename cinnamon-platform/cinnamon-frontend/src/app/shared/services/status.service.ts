import { Injectable } from '@angular/core';
import { List } from "../../core/utils/list";
import { Steps } from "../../core/enums/steps";
import { Status } from "../model/status";
import { Mode } from "../../core/enums/mode";
import { HttpClient } from "@angular/common/http";
import { environments } from "../../../environments/environment";
import { Observable, ReplaySubject } from "rxjs";
import { ErrorHandlingService } from "./error-handling.service";

@Injectable({
    providedIn: 'root'
})
export class StatusService {
    private readonly baseUrl: string = environments.apiUrl + "/api/project"

    private _status: Status;
    private statusSubject: ReplaySubject<Status> | null = null;

    /**
     * List of all completed steps.
     * @private
     */
    private readonly completedSteps: List<Steps> = new List();

    constructor(
        private readonly errorHandlingService: ErrorHandlingService,
        private readonly http: HttpClient,
    ) {
    }

    public get status$(): Observable<Status> {
        return this.initializeStatus().asObservable();
    }

    private initializeStatus(): ReplaySubject<Status> {
        if (this.statusSubject === null) {
            this.statusSubject = new ReplaySubject<Status>(1);

            this.http.get<Status>(this.baseUrl + "/status").subscribe({
                next: (value: Status) => {
                    this._status = value;
                    this.setNextStep(value.currentStep);
                    this.statusSubject?.next(value);
                },
                error: err => {
                  this.errorHandlingService.addError(err, "Failed to fetch project state.");
                },
            });
        }

        return this.statusSubject;
    }

    /**
     * Sets the mode to the given value and synchronizes the status with the backend.
     * @param mode The selected mode.
     */
    public setMode(mode: Mode): Observable<void> {
        this._status.mode = mode;
        this.initializeStatus().next(this._status);

        const formData = new FormData();
        formData.append("mode", mode.toString());
        return this.http.post<void>(this.baseUrl, formData);
    }

    getCompletedSteps(): List<Object> {
        return this.completedSteps;
    }

    /**
     * Sets the given step to the current steps, marks all previous steps as completed and updates the backend.
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
    setNextStep(step: Steps): void {
        this._status.currentStep = step;
        this.initializeStatus().next(this._status);

        this.completedSteps.clear();

        Object.entries(Steps).forEach(([key, value]) => {
            const currentIndex = typeof step === "number" ? step : Steps[step];
            if (key < currentIndex) {
                this.addCompletedStep(Steps[value as keyof typeof Steps]);
            }
        });
    }

    private postStep(step: Steps): Observable<void> {
        const stepValue = typeof step === "number" ? Steps[step] : step;

        const formData = new FormData();
        formData.append("step", stepValue);
        return this.http.post<void>(this.baseUrl + "/step", formData);
    }

    addCompletedStep(step: Steps): void {
        if (!this.completedSteps.contains(step)) {
            this.completedSteps.add(step);
        }
    }

    removeCompletedStep(step: Steps): void {
        if (this.completedSteps.contains(step)) {
            this.completedSteps.remove(step);
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
