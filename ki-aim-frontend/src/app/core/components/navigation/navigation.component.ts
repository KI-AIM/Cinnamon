import { Component, TemplateRef, ViewChild } from '@angular/core';
import { StateManagementService } from '../../services/state-management.service';
import { Mode } from '../../enums/mode';
import { StepConfiguration, Steps } from '../../enums/steps';
import { KeyValue } from '@angular/common';
import { UserService } from 'src/app/shared/services/user.service';

@Component({
    selector: 'app-navigation',
    templateUrl: './navigation.component.html',
    styleUrls: ['./navigation.component.less'],
})

export class NavigationComponent {
    Mode = Mode;
    Steps = Steps;
    StepConfiguration = StepConfiguration; 

    constructor(
        public stateManagement: StateManagementService,
        public userService: UserService,
    ) { }

    disableNavLink(id: String) {
    }


    indexOrderAsc = (akv: KeyValue<string, any>, bkv: KeyValue<string, any>): number => {
        const a = akv.value.index;
        const b = bkv.value.index;

        return a > b ? 1 : (b > a ? -1 : 0);
    };

    onLogout() {
        this.userService.logout();
    }
}
