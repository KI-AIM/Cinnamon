import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from "rxjs";

/**
 * Service for managing the content of the navigation component {@link NavigationComponent}.
 *
 * @author Daniel Preciado-Marquez
 */
@Injectable({
    providedIn: 'root'
})
export class NavigationService {

    // Hacked in state for the admin interface
    private isAdmin: BehaviorSubject<boolean>;

    constructor() {
        this.isAdmin = new BehaviorSubject<boolean>(false);
    }

    public get isAdmin$(): Observable<boolean> {
        return this.isAdmin.asObservable();
    }

    public setAdmin(isAdmin: boolean) {
        this.isAdmin.next(isAdmin);
    }

}
