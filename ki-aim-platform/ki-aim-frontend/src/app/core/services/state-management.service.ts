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
import { environments } from "../../../environments/environment";
import { StatusService } from "../../shared/services/status.service";

@Injectable({
    providedIn: 'root'
})
export class StateManagementService {

    constructor(
        private readonly configurationService: ConfigurationService,
        private readonly router: Router,
        private readonly userService: UserService,
        private readonly statusService: StatusService
    ) {
        if (this.userService.isAuthenticated()) {
            this.fetchCurrentStep();
        }
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
            if (a === this.statusService.currentStep.toString()) {
                this.router.navigateByUrl(b.path);
                return;
            }

        }
        this.router.navigateByUrl("/start");
    }

    /**
     * Fetches the state and configurations from the backend.
     * @private
     */
    private doFetchCurrentStep(): Observable<Status> {
        return this.statusService.fetchStatus().pipe(tap(value => {
            this.configurationService.fetchConfigurations(this.statusService.currentStep);
        }));
    }
}
