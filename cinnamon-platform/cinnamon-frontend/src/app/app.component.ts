import { Component } from '@angular/core';
import { TitleService } from './core/services/title-service.service';
import { StateManagementService } from './core/services/state-management.service';

@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.less'],
    providers: [],
})
export class AppComponent {
    title = "cinnamon-frontend"
    constructor(private titleService: TitleService, stateManagement: StateManagementService) {
    }

    getTitle(): String {
        return this.titleService.getPageTitle();
    }
}
