import { animate, style, transition, trigger } from "@angular/animations";
import { Component, OnInit, TemplateRef } from '@angular/core';
import { MatDialog } from "@angular/material/dialog";
import { Steps } from "@core/enums/steps";
import { AppNotification, NotificationService } from "@core/services/notification.service";
import { StatusService } from "@shared/services/status.service";
import { TitleService } from './core/services/title-service.service';
import { AppConfig, AppConfigService } from "./shared/services/app-config.service";
import { Observable, switchMap } from "rxjs";
import { LockedInformation, LockedReason, StateManagementService } from "./core/services/state-management.service";
import { ErrorHandlingService } from "./shared/services/error-handling.service";

@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.less'],
    providers: [],
    standalone: false,
    animations: [
        trigger('slideInFromTop', [
            transition(':enter', [
                style({ transform: 'translateY(-100%)', opacity: 0 }),
                animate('300ms ease-out', style({ transform: 'translateY(0)', opacity: 1 }))
            ]),
            transition(':leave', [
                animate('300ms ease-in', style({ transform: 'translateY(-100%)', opacity: 0 }))
            ]),
        ]),
    ],
})
export class AppComponent implements OnInit {
    title = "cinnamon-frontend"

    protected readonly StatusService = StatusService;

    protected appConfig$: Observable<AppConfig>;
    protected errorList$: Observable<string[]>;
    protected latestNotification$: Observable<AppNotification | null>;
    protected locked$: Observable<LockedInformation>;

    constructor(
        private readonly appConfigService: AppConfigService,
        protected readonly errorHandlingService: ErrorHandlingService,
        private readonly dialog: MatDialog,
        protected readonly notificationService: NotificationService,
        // StateManagementService is injected so it gets initialized
        private readonly stateManagementService: StateManagementService,
        private readonly statusService: StatusService,
        private titleService: TitleService,
    ) {
    }

    public ngOnInit(): void {
        this.appConfig$ = this.appConfigService.appConfig$;
        this.errorList$ = this.errorHandlingService.errorList$;
        this.latestNotification$ = this.notificationService.latestNotification$();
        this.locked$ = this.stateManagementService.currentStepLocked$;
    }

    getTitle(): String {
        return this.titleService.getPageTitle();
    }

    /**
     * Opens the dialog for deleting all results and unlocking the data import.
     *
     * @param templateRef The Reference for the dialog template.
     * @protected
     */
    protected openDeleteDialog(templateRef: TemplateRef<any>): void {
        this.dialog.open(templateRef, {
            disableClose: true,
            width: '60%'
        });
    }

    /**
     * Unlocks the given step.
     * If the step is part of the data import, deletes all results.
     *
     * @param step The step to unlock.
     * @protected
     */
    protected deleteData(step: Steps): void {
        this.stateManagementService.unlockStep(step).pipe(
            switchMap(() => {
                return this.statusService.updateNextStep(step);
            }),
        ).subscribe({
            error: error => {
                this.errorHandlingService.addError(error);
            },
        });
    }

    protected readonly LockedReason = LockedReason;
}
