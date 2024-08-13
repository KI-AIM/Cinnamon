import { Component, TemplateRef, ViewChild } from '@angular/core';
import { MatDialog } from "@angular/material/dialog";
import { Steps } from "../../../core/enums/steps";
import { ConfigurationService } from 'src/app/shared/services/configuration.service';
import { StateManagementService } from 'src/app/core/services/state-management.service';

@Component({
  selector: 'app-configuration-management',
  templateUrl: './configuration-management.component.html',
  styleUrls: ['./configuration-management.component.less']
})
export class ConfigurationManagementComponent {
    protected readonly Steps = Steps;
    protected error: string;

    @ViewChild('configurationManagement') dialogWrap: TemplateRef<any>;

    constructor(
        public configurationService: ConfigurationService,
        public dialog: MatDialog,
        public stateManagementService: StateManagementService,
    ) {
        this.error = "";
    }

    /**
     * Opens the dialog.
     * @param templateRef Reference of the dialog.
     */
    openDialog() {
        this.dialog.open(this.dialogWrap, {
            width: '60%'
        });
    }

    /**
     * Downloads all registered configurations.
     * Uses the getConfigCallback function to retrieve the configuration.
     * If configured, stores the configuration under the configured name into the database.
     */
    downloadAllConfigurations() {
        const included = [];
        for (const config of this.configurationService.getRegisteredConfigurations()) {
            if ((document.getElementById(config.name + "-input") as HTMLInputElement).checked) {
                included.push(config.name);
            }
        }
        this.configurationService.downloadAllConfigurations(included);
    }

}
