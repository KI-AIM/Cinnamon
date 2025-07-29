import { Component, OnInit } from '@angular/core';
import { StepDefinition } from "@core/enums/steps";
import { PipelineInformation } from "@shared/model/execution-step";
import { Status } from "@shared/model/status";
import { StatusService } from "@shared/services/status.service";
import { TitleService } from './core/services/title-service.service';
import { AppConfig, AppConfigService } from "./shared/services/app-config.service";
import { Observable } from "rxjs";
import { StateManagementService } from "./core/services/state-management.service";
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
    protected currentStep$: Observable<StepDefinition | null>;
    protected errorList$: Observable<string[]>;
    protected pipeline$: Observable<PipelineInformation>;
    protected status$: Observable<Status>;

    constructor(
        private readonly appConfigService: AppConfigService,
        protected readonly errorHandlingService: ErrorHandlingService,
        // StateManagementService is injected so it gets initialized
        protected readonly stateManagementService: StateManagementService,
        protected readonly statusService: StatusService,
        private titleService: TitleService,
    ) {
    }

    public ngOnInit(): void {
        this.appConfig$ = this.appConfigService.appConfig$;
        this.currentStep$ = this.stateManagementService.currentStep$;
        this.errorList$ = this.errorHandlingService.errorList$;
        this.pipeline$ = this.stateManagementService.pipelineInformation$;
        this.status$ = this.statusService.status$;
    }

    getTitle(): String {
        return this.titleService.getPageTitle();
    }
}
