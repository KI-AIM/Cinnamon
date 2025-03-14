import { Injectable } from '@angular/core';
import { List } from "../../core/utils/list";
import { Steps } from "../../core/enums/steps";
import { Status } from "../model/status";
import { Mode } from "../../core/enums/mode";
import { HttpClient } from "@angular/common/http";
import { environments } from "../../../environments/environment";
import { finalize, Observable, of, share, tap } from "rxjs";

@Injectable({
  providedIn: 'root'
})
export class StatusService {
    private readonly baseUrl: string = environments.apiUrl + "/api/project"

    private status: Status;
    private status$: Observable<Status> | null = null;
    private fetched: boolean = false;

    /**
     * List of all completed steps.
     * @private
     */
    private readonly completedSteps: List<Steps>;

  constructor(
      private readonly http: HttpClient,
  ) {
      this.status = new Status()
      this.status.mode = Mode.UNSET;
      this.status.currentStep = Steps.WELCOME;
      this.completedSteps = new List();
  }

  public get currentStep(): Steps {
      return this.status.currentStep;
  }

    /**
     * Sets the mode to the given value and synchronizes the status with the backend.
     * @param mode The selected mode.
     */
    setMode(mode: Mode): Observable<void> {
        this.status.mode = mode;

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
        this.status.currentStep = step;

        this.completedSteps.clear();

        Object.entries(Steps).forEach(([key, value]) => {
            const currentIndex = typeof step === "number" ? step : Steps[step];
            if (key < currentIndex) {
                this.addCompletedStep(Steps[value as keyof typeof Steps]);
            }
        });
    }

    /**
     * Fetches the status from the backend.
     */
    public fetchStatus(): Observable<Status> {
        if (this.fetched) {
            return of(this.status);
        }

        if (this.status$) {
            return this.status$;
        }

        this.status$ = this.http.get<Status>(this.baseUrl + "/status")
            .pipe(
                tap(value => {
                    this.fetched = true;
                    this.status = value;
                    this.setNextStep(value.currentStep);
                }),
                share(),
                finalize(() => {
                    this.status$ = null;
                }),
            );

        return this.status$;
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
