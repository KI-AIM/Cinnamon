import { Injectable } from '@angular/core';
import { Mode } from '../enums/mode';
import { StepConfiguration, Steps } from '../enums/steps';
import { List } from '../utils/list';
import { Status } from "../../shared/model/status";
import { HttpClient } from "@angular/common/http";
import { Router } from "@angular/router";
import { UserService } from "../../shared/services/user.service";
import { Observable, of, tap } from "rxjs";
import { ConfigurationService } from "../../shared/services/configuration.service";

@Injectable({
    providedIn: 'root'
})
export class StateManagementService {

    private status: Status;
    private fetched: boolean = false;

    private readonly completedSteps: List<Steps>;

    constructor(
        private readonly configurationService: ConfigurationService,
        private readonly http: HttpClient,
        private readonly router: Router,
        private readonly userService: UserService,
    ) {
        this.status = new Status()
        this.status.mode = Mode.UNSET;
        this.status.currentStep = Steps.WELCOME;
        this.completedSteps = new List();

        if (this.userService.isAuthenticated()) {
            this.fetchCurrentStep();
        }
    }

    getMode(): Mode {
        return this.status.mode;
    }

    /**
     * Sets the mode to the given value and synchronizes the status with the backend.
     * @param mode The selected mode.
     */
    setMode(mode: Mode) {
        this.status.mode = mode;
        this.http.post("/api/project/", this.status)

        const formData = new FormData();

        formData.append("mode", mode.toString());
        this.http.post("/api/project", formData).subscribe({
            error: err => {
                console.log(err);
            }
        });
    }

    getCompletedSteps(): List<Object> {
        return this.completedSteps;
    }

    /**
     * Sets the given step to the current steps and marks all previous steps as completed.
     * Steps after the given step will be removed from the list of completed steps.
     *
     * @param step The next step.
     */
    setNextStep(step: Steps): void {
        this.status.currentStep = step;

        this.completedSteps.clear();

        Object.entries(Steps).forEach(([key, value]) => {
            const currentIndex = typeof step === "number" ? step : Steps[step];
            console.log(currentIndex);
            if (key < currentIndex) {
                this.addCompletedStep(Steps[value as keyof typeof Steps]);
            }
        });
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
     * If the step is null, returns false.
     *
     * @param step The step to check.
     * @returns If the given step is completed.
     */
    isStepCompleted(step: Steps | null) {
        if (step == null) {
            return false;
        }
        return this.completedSteps.contains(step);
    }

    /**
     * Fetches the state and configurations from the backend.
     */
    public fetchCurrentStep() {
        this.doFetchCurrentStep().subscribe();
    }

    /**
     * Fetches the state and configurations from the backend and routes to the current step.
     */
    public fetchAndRouteToCurrentStep() {
        this.doFetchCurrentStep().subscribe({
            next: value => {
                this.routeToCurrentStep();
            }
        });
    }

    /**
     * Routes to the page for the current step.
     */
    public routeToCurrentStep() {
        for (let [a, b] of Object.entries(StepConfiguration)) {
            if (a === this.status.currentStep.toString()) {
                this.router.navigateByUrl(b.path);
                return;
            }

        }
        this.router.navigateByUrl("/start");
    }

    /**
     * Fetches the status from the backend.
     */
    public fetchStatus(): Observable<Status> {
        if (this.fetched) {
            return of(this.status);
        }

        return this.http.get<Status>("/api/project/status")
            .pipe(
                tap(value => {
                    this.fetched = true;
                    this.status = value;
                    this.setNextStep(value.currentStep);
                }),
            );
    }

    /**
     * Fetches the state and configurations from the backend.
     * @private
     */
    private doFetchCurrentStep(): Observable<Status> {
        return this.fetchStatus().pipe(tap(value => {
            this.configurationService.fetchConfigurations(this.status.currentStep);
        }));
    }
}
