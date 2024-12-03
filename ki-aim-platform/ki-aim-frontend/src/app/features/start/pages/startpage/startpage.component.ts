import { Component } from '@angular/core';
import { StateManagementService } from 'src/app/core/services/state-management.service';
import { TitleService } from 'src/app/core/services/title-service.service';
import { Mode } from 'src/app/core/enums/mode';
import { Steps } from 'src/app/core/enums/steps';
import { StatusService } from "../../../../shared/services/status.service";

@Component({
    selector: 'app-startpage',
    templateUrl: './startpage.component.html',
    styleUrls: ['./startpage.component.less'],
    providers: []
})
export class StartpageComponent {
    Mode = Mode;
    Steps = Steps;

    constructor(private titleService: TitleService, public statusService : StatusService) {
        this.titleService.setPageTitle("Welcome!");
    }

    protected get locked(): boolean {
        return this.statusService.isStepCompleted(Steps.UPLOAD);
    }
}
