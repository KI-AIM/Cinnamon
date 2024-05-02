import { Component, TemplateRef, ViewChild } from '@angular/core';
import { MatDialog } from "@angular/material/dialog";
import { Steps } from "../../../../core/enums/steps";
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

    /**
     * Uploads all configurations contained in the file.
     * Uses the setConfigCallback function to update the configuration in the application.
     * If configured, stores the configuration under the configured name into the database.
     * @param event Input event of the file input.
     */
    uploadAllConfigurations() {
        const files = (document.getElementById("configInput") as HTMLInputElement).files;
        if (!files || files.length === 0) {
            return;
        }

        const included = [];
        for (const config of this.configurationService.getRegisteredConfigurations()) {
            if ((document.getElementById(config.name + "-input") as HTMLInputElement).checked) {
                included.push(config.name);
            }
        }
        this.configurationService.uploadAllConfigurations(files[0], included);
    }
}
