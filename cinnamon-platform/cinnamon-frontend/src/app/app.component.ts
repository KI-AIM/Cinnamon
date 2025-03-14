import { Component, OnInit } from '@angular/core';
import { TitleService } from './core/services/title-service.service';
import { AppConfig, AppConfigService } from "./shared/services/app-config.service";
import { Observable } from "rxjs";
import { StateManagementService } from "./core/services/state-management.service";

@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.less'],
    providers: [],
})
export class AppComponent implements OnInit {
    title = "cinnamon-frontend"

    protected appConfig$: Observable<AppConfig>;

    constructor(
        private readonly appConfigService: AppConfigService,
        // StateManagementService is injected so it gets initialized
        private readonly stateManagementService: StateManagementService,
        private titleService: TitleService,
    ) {
    }

    public ngOnInit(): void {
        this.appConfig$ = this.appConfigService.appConfig$;
    }

    getTitle(): String {
        return this.titleService.getPageTitle();
    }
}
