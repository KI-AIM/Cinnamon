import { Injectable } from '@angular/core';
import { StepConfiguration } from '../enums/steps';
import { Status } from "@shared/model/status";
import { Router } from "@angular/router";
import { UserService } from "@shared/services/user.service";
import { take } from "rxjs";
import { StatusService } from "@shared/services/status.service";

@Injectable({
    providedIn: 'root'
})
export class StateManagementService {

    constructor(
        private readonly router: Router,
        private readonly userService: UserService,
        private readonly statusService: StatusService
    ) {
        if (this.userService.isAuthenticated()) {
            this.fetchCurrentStep();
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
}
