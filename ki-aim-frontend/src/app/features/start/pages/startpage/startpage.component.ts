import { Component } from '@angular/core';
import { StateManagementService } from 'src/app/core/services/state-management.service';
import { TitleService } from 'src/app/core/services/title-service.service';
import { Mode } from 'src/app/core/enums/mode';
import { Steps } from 'src/app/core/enums/steps';

@Component({
    selector: 'app-startpage',
    templateUrl: './startpage.component.html',
    styleUrls: ['./startpage.component.less'],
    providers: []
})
export class StartpageComponent {
    Mode = Mode;
    Steps = Steps; 

    constructor(private titleService: TitleService, public stateManagement: StateManagementService) {
        this.titleService.setPageTitle("Welcome!"); 
    }

}
