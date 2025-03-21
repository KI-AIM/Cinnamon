import { Component, TemplateRef, ViewChild } from '@angular/core';
import { Mode } from '../../enums/mode';
import { StepConfiguration, Steps } from '../../enums/steps';
import { KeyValue } from '@angular/common';
import { UserService } from 'src/app/shared/services/user.service';
import { ConfigurationManagementComponent } from 'src/app/shared/components/configuration-management/configuration-management.component';
import { StatusService } from "../../../shared/services/status.service";
import { environments } from "../../../../environments/environment";
import { HttpClient } from "@angular/common/http";
import { MatDialog } from "@angular/material/dialog";

@Component({
    selector: 'app-navigation',
    templateUrl: './navigation.component.html',
    styleUrls: ['./navigation.component.less'],
    standalone: false
})

export class NavigationComponent {
    Mode = Mode;
    Steps = Steps;
    StepConfiguration = StepConfiguration;

    @ViewChild(ConfigurationManagementComponent) configManagement: ConfigurationManagementComponent;

    constructor(
        private readonly dialog: MatDialog,
        private readonly http: HttpClient,
        protected statusService: StatusService,
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

    openConfigurations() {
        this.configManagement.openDialog();
    }

    /**
     * Downloads all files related to the project in a ZIP file.
     * @protected
     */
    protected downloadResult() {
        this.http.get(environments.apiUrl + "/api/project/zip", {responseType: 'arraybuffer'}).subscribe({
            next: data => {
                const blob = new Blob([data], {
                    type: 'application/zip'
                });
                const url = window.URL.createObjectURL(blob);
                window.open(url);
            }
        });
    }

    protected openDialog(templateRef: TemplateRef<any>) {
        this.dialog.open(templateRef, {
            width: '60%'
        });
    }

}
