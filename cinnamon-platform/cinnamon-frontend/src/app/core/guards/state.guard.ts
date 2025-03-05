import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Observable } from 'rxjs';
import { StateManagementService } from "../services/state-management.service";

@Injectable({
    providedIn: 'root'
})
export class StateGuard implements CanActivate {

    constructor(
        private readonly stateManagement: StateManagementService
    ) {
    }

    canActivate(
        route: ActivatedRouteSnapshot,
        state: RouterStateSnapshot): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
        this.stateManagement.fetchAndRouteToCurrentStep();
        return true;
    }

}
