import { Component, OnInit } from '@angular/core';
import { StatusService } from "@shared/services/status.service";
import { TitleService } from './core/services/title-service.service';
import { AppConfig, AppConfigService } from "./shared/services/app-config.service";
import { Observable } from "rxjs";
import { LockedInformation, LockedReason, StateManagementService } from "./core/services/state-management.service";
import { ErrorHandlingService } from "./shared/services/error-handling.service";

@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.less'],
    providers: [],
    standalone: false
})
export class AppComponent implements OnInit {
    title = "cinnamon-frontend"

    protected readonly StatusService = StatusService;

    protected appConfig$: Observable<AppConfig>;
    protected errorList$: Observable<string[]>;
    protected locked$: Observable<LockedInformation>;

    constructor(
        private readonly appConfigService: AppConfigService,
        protected readonly errorHandlingService: ErrorHandlingService,
        // StateManagementService is injected so it gets initialized
        protected readonly stateManagementService: StateManagementService,
        private titleService: TitleService,
    ) {
    }

    public ngOnInit(): void {
        this.appConfig$ = this.appConfigService.appConfig$;
        this.errorList$ = this.errorHandlingService.errorList$;
        this.locked$ = this.stateManagementService.currentStepLocked$;
    }

    getTitle(): String {
        return this.titleService.getPageTitle();
    }

    protected readonly LockedReason = LockedReason;
}
