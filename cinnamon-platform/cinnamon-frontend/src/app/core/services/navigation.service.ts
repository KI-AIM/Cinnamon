import { Injectable } from '@angular/core';
import { NavigationKey } from "@shared/model/navigation";
import { BehaviorSubject, distinctUntilChanged, Observable } from "rxjs";

/**
 * Service for managing the content of the navigation component {@link NavigationComponent}.
 *
 * @author Daniel Preciado-Marquez
 */
@Injectable({
    providedIn: 'root'
})
export class NavigationService {

    private isAdmin: BehaviorSubject<boolean>;
    private navigationKey: BehaviorSubject<NavigationKey>;

    constructor() {
        this.navigationKey = new BehaviorSubject<NavigationKey>(NavigationKey.NONE);
        this.isAdmin = new BehaviorSubject<boolean>(false);
    }

    public get navigationKey$(): Observable<NavigationKey> {
        return this.navigationKey.asObservable().pipe(
            distinctUntilChanged(),
        );
    }

    public setNavigationKey(navigationKey: NavigationKey) {
        this.navigationKey.next(navigationKey);
    }

}
