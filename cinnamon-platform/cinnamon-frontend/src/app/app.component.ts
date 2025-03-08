import { Component } from '@angular/core';
import { TitleService } from './core/services/title-service.service';
import { AppConfigService } from "./shared/services/app-config.service";

@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.less'],
    providers: [],
})
export class AppComponent {
    title = "cinnamon-frontend"

    constructor(
        protected readonly appConfigService: AppConfigService,
        private titleService: TitleService,
    ) {
    }

    getTitle(): String {
        return this.titleService.getPageTitle();
    }
}
