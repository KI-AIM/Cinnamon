import { Injectable } from '@angular/core';
import { Event, NavigationEnd, Router } from "@angular/router";
import { NavigationKey } from "@shared/model/navigation";
import { BehaviorSubject, distinctUntilChanged, Observable } from "rxjs";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";

/**
 * Service for managing the content of the navigation component {@link NavigationComponent}.
 *
 * @author Daniel Preciado-Marquez
 */
@Injectable({
    providedIn: 'root'
})
export class NavigationService {

    private navigationKey: BehaviorSubject<NavigationKey>;

    constructor(
        private readonly router: Router,

    ) {
        this.navigationKey = new BehaviorSubject<NavigationKey>(NavigationKey.NONE);

        this.router.events.pipe(
            takeUntilDestroyed(),
        ).subscribe((event: Event): void => {
            if (event instanceof NavigationEnd) {
                const url = (event as NavigationEnd).url;
                if (url.includes("project")) {
                    this.setNavigationKey(NavigationKey.PROJECT);
                } else if (url.includes("admin")) {
                    this.setNavigationKey(NavigationKey.ADMIN);
                } else if (url.includes("user")) {
                    this.setNavigationKey(NavigationKey.USER);
                } else {
                    this.setNavigationKey(NavigationKey.NONE);
                }
            }
        });
    }

    /**
     * Observable for the current navigation key.
     */
    public get navigationKey$(): Observable<NavigationKey> {
        return this.navigationKey.asObservable().pipe(
            distinctUntilChanged(),
        );
    }

    /**
     * Sets the navigation key.
     * @param navigationKey The navigation key to set.
     */
    public setNavigationKey(navigationKey: NavigationKey): void {
        this.navigationKey.next(navigationKey);
    }

}
