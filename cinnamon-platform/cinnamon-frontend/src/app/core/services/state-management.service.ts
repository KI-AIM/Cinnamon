import { Injectable } from '@angular/core';
import { StepConfiguration, StepDefinition, Steps } from '../enums/steps';
import { Status } from "@shared/model/status";
import { NavigationEnd, Router } from "@angular/router";
import { UserService } from "@shared/services/user.service";
import { filter, map, Observable, take } from "rxjs";
import { StatusService } from "@shared/services/status.service";

@Injectable({
    providedIn: 'root'
})
export class StateManagementService {

    public readonly currentStep$: Observable<StepDefinition | null>;

    constructor(
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
     * Unlocks the given step by resetting subsequent steps.
     * @param unlock The step to unlock.
     */
    public unlockStep(unlock: Steps): void {
        // TODO implement
    }
}
