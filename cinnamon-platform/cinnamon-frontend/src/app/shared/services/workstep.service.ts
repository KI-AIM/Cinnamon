import { Injectable } from '@angular/core';
import { BehaviorSubject } from "rxjs";

@Injectable({
    providedIn: 'root'
})
export class WorkstepService {

    private numberSteps = 0;
    private stepSubject = new BehaviorSubject<number>(0);


    /**
     * The observable that emits the current step of the workstep service.
     */
    public get step$() {
        return this.stepSubject.asObservable();
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
        return this.currentStep >= this.numberSteps;
    }

    /**
     * Initializes the workstep service with the number of steps and whether it is finished or not.
     * @param numberSteps The number of steps to be completed.
     * @param finished If all worksteps have been finished in the past.
     */
    public init(numberSteps: number, finished: boolean): void {
        this.numberSteps = numberSteps;
        this.stepSubject.next(finished ? numberSteps : 0);
    }

    /**
     * Marks the given step as completed.
     * @param stepIndex
     */
    public confirmStep(stepIndex: number): void {
        this.stepSubject.next(stepIndex + 1);
    }
}
