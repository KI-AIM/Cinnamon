import { Injectable } from '@angular/core';
import { Mode } from '../enums/mode';
import { Steps } from '../enums/steps';
import { List } from '../utils/list';

@Injectable({
    providedIn: 'root'
})
export class StateManagementService {

    private mode: Mode
    private completedSteps: List<Steps>;

    constructor() {
        this.mode = Mode.UNSET;
        this.completedSteps = new List();
    }

    getMode(): Mode {
        return this.mode;
    }

    setMode(mode: Mode) {
        this.mode = mode;
    }

    getCompletedSteps(): List<Steps> {
        return this.completedSteps;
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
}
