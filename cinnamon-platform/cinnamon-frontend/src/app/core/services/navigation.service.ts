import { Injectable, OnInit } from '@angular/core';
import { ActivatedRoute } from "@angular/router";
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
export class NavigationService implements OnInit {

    private navigationKey: BehaviorSubject<NavigationKey>;

    constructor(
        private readonly route: ActivatedRoute,
    ) {
        this.navigationKey = new BehaviorSubject<NavigationKey>(NavigationKey.NONE);
    }

    public ngOnInit(): void {
        this.route.url.subscribe(url => {
            if (url.some(segment => segment.path === "project")) {
                this.setNavigationKey(NavigationKey.PROJECT);
            } else if (url.some(segment => segment.path === "admin")) {
                // Check admin before user because the admin route is part of the user route
                this.setNavigationKey(NavigationKey.ADMIN);
            } else if (url.some(segment => segment.path === "user")) {
                this.setNavigationKey(NavigationKey.USER);
            }
        });

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
