import { Injectable, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from "@angular/router";
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
        private readonly router: Router,
    ) {
        this.navigationKey = new BehaviorSubject<NavigationKey>(NavigationKey.NONE);
    }

    public ngOnInit(): void {
        this.router.events.subscribe((event) => {
            ev
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
