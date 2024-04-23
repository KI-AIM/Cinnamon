import { Component, TemplateRef } from '@angular/core';
import { MatDialog } from "@angular/material/dialog";
import { FileUtilityService } from "src/app/shared/services/file-utility.service";
import { FileService } from "src/app/features/data-upload/services/file.service";
import { StateManagementService } from "src/app/core/services/state-management.service";
import { Steps } from "../../../../core/enums/steps";
import { ConfigurationService } from 'src/app/shared/services/configuration.service';
import { ConfigurationRegisterData } from 'src/app/shared/model/configuration-register-data';

@Component({
  selector: 'app-configuration-management',
  templateUrl: './configuration-management.component.html',
  styleUrls: ['./configuration-management.component.less']
})
export class ConfigurationManagementComponent {
    protected readonly Steps = Steps;
    protected error: string;

    constructor(
        public configurationService: ConfigurationService,
        public dialog: MatDialog,
        public fileService: FileService,
        public fileUtilityService: FileUtilityService,
        public stateManagementService: StateManagementService,
    ) {
        this.error = "";
    }

    /**
     * Opens the dialog.
     * @param templateRef Reference of the dialog.
     */
    openDialog(templateRef: TemplateRef<any>) {
        this.dialog.open(templateRef, {
            width: '60%'
        });
    }

    /**
     * Downloads the configuration based on the given configuration data.
     * Uses the getConfigCallback function to retrieve the configuration.
     * If configured, stores the configuration under the configured name into the database.
     * 
     * @param config The register data of the configuration to download.
     */
    downloadConfiguration(config: ConfigurationRegisterData) {
        const configData = config.getConfigCallback();

        if (config.syncWithBackend) {
            this.configurationService.storeConfig(config.name, configData).subscribe({
                error: (error) => {
                    this.error = error;
                },
            });
        }

        const blob = new Blob([configData], { type: 'text/yaml' });
        const fileName = this.fileService.getFile().name + "-" + config.name + "-configuration.yaml"
        this.fileUtilityService.saveFile(blob, fileName);
    }

    /**
     * Uploads the configuration from the target of the given event as the configuration of the given configuration register data.
     * Uses the setConfigCallback function to update the configuration in the application.
     * If configured, stores the configuration under the configured name into the database.
     * 
     * @param config The register data of the configuration to upload.
     * @param event Input event of the file input.
     */
    uploadConfiguration(config: ConfigurationRegisterData, event: Event) {
        const files = (event.target as HTMLInputElement)?.files;

        if (files) {
            const reader = new FileReader();
            reader.addEventListener("load", () => {
                const configData = reader.result as string;

                if (config.syncWithBackend) {
                    this.configurationService.storeConfig(config.name, configData).subscribe({
                        error: (error) => {
                            this.error = error;
                        },
                    });
                }

                config.setConfigCallback(reader.result as string);
            }, false);

            reader.readAsText(files[0]);
        }
    }
}
