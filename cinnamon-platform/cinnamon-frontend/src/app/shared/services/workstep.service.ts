import { Injectable } from '@angular/core';
import { Steps } from "@core/enums/steps";
import { BehaviorSubject } from "rxjs";

/**
 * Central service for handling the logic of worksteps.
 *
 * The {@link #step$} observable emits the step that should be opened.
 * The {@link WorkstepTitleComponent} listens to this and opens the workstep if the index matches.
 * The {@link WorkstepComponent} triggers the next value of the {@link #step$} observable when a user confirms the step.
 *
 * @author Daniel Preciado-Marquez
 */
@Injectable({
    providedIn: 'root'
})
export class WorkstepService {

    // Current state
    private _isFinished = false;
    private numberSteps = 0;
    private stepSubject = new BehaviorSubject<number>(0);
    private openStepSubject = new BehaviorSubject<number>(0);

    // Saved state from the last unfinished step
    private stateCurrent: Steps | null = null;
    private stateCurrentStep: number = 0;

    /**
     * The observable that emits the current step of the workstep service.
     */
    public get step$() {
        return this.stepSubject.asObservable();
    }

    public get openedStep$() {
        return this.openStepSubject.asObservable();
    }

    /**
     * If all worksteps have been completed in the past.
     */
    public get isFinished() {
        return this._isFinished;
    }

    /**
     * The current step of the workstep service.
     */
    public get currentStep(): number {
        return this.stepSubject.getValue();
    }

    /**
     * If all steps are completed.
     */
    public get isCompleted(): boolean {
        return this._isFinished || this.currentStep >= this.numberSteps;
    }

    /**
     * Initializes the workstep service with the number of steps and whether it is finished or not.
     * @param step The step, used for saving the state.
     * @param numberSteps The number of steps to be completed.
     * @param finished If all worksteps have been finished in the past.
     * @param reset If the progress should be reset.
     */
    public init(step: Steps, numberSteps: number, finished: boolean, reset: boolean): void {
        this.numberSteps = numberSteps;
        this._isFinished = finished;

        if (step === this.stateCurrent && !reset) {
            // Restore the previous state
            this.stepSubject.next(this.stateCurrentStep);
            this.openStepSubject.next(this.stateCurrentStep);
        } else {
            if (!finished) {
                this.stateCurrent = step;
            }

            this.stepSubject.next(finished ? numberSteps : 0);
            this.openStepSubject.next(finished ? numberSteps : 0);
        }

    }

    /**
     * Saves the state if the current step is unfinished
     */
    public shutdown(): void {
        if (!this.isFinished) {
            // Save the state
            this.stateCurrentStep = this.currentStep;
        }
    }

    /**
     * Marks the given step as completed.
     * @param stepIndex
     */
    public confirmStep(stepIndex: number): void {
        this.stepSubject.next(stepIndex + 1);
    }

    /**
     * Marks all steps as completed.
     */
    public confirmAllSteps(): void {
        this.stepSubject.next(this.numberSteps);
    }

    public openStep(stepIndex: number): void {
        this.openStepSubject.next(stepIndex);
    }
}
