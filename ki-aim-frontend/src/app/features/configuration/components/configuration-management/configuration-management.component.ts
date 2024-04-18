import { Component, TemplateRef } from '@angular/core';
import { MatDialog } from "@angular/material/dialog";
import { DataConfigurationService } from 'src/app/shared/services/data-configuration.service';
import { FileUtilityService } from "src/app/shared/services/file-utility.service";
import { FileService } from "src/app/features/data-upload/services/file.service";
import { StateManagementService } from "src/app/core/services/state-management.service";
import { Steps } from "../../../../core/enums/steps";
import { DataConfiguration } from "../../../../shared/model/data-configuration";

@Component({
  selector: 'app-configuration-management',
  templateUrl: './configuration-management.component.html',
  styleUrls: ['./configuration-management.component.less']
})
export class ConfigurationManagementComponent {
    protected readonly Steps = Steps;
    protected error: string;

    constructor(
        public dataConfigurationService: DataConfigurationService,
        public dialog: MatDialog,
        public fileService: FileService,
        public fileUtilityService: FileUtilityService,
        public stateManagementService: StateManagementService,
    ) {
        this.error = "";
    }

    openDialog(templateRef: TemplateRef<any>) {
        this.dialog.open(templateRef, {
            width: '60%'
        });
    }

    downloadDataConfiguration() {
        this.dataConfigurationService.downloadDataConfigurationAsYaml().subscribe({
            next: (data: Blob) => {
                const blob = new Blob([data], { type: 'text/yaml' });
                const fileName = this.fileService.getFile().name + "-configuration.yaml"
                this.fileUtilityService.saveFile(blob, fileName);
            },
            error: (error) => {
                this.error = error;
            },
        });
    }

    uploadDataConfiguration(event: Event) {
        const files = (event.target as HTMLInputElement)?.files;

        if (files) {
            const reader = new FileReader();
            reader.addEventListener("load", () => {
                const a = this.dataConfigurationService.postDataConfigurationString(reader.result as string);
                a.subscribe({
                    next: (data: Number) => {
                        const dataConfiguration = this.dataConfigurationService.downloadDataConfigurationAsJson();
                        dataConfiguration.subscribe({
                            next: (data: DataConfiguration) => {
                                this.dataConfigurationService.setDataConfiguration(data);
                            },
                            error: (error) => {
                                this.error = error;
                            },
                        });
                    },
                    error: (error) => {
                        this.error = error;
                    },
                });
            }, false);

            reader.readAsText(files[0]);
        }
    }

}

class ConfigurationData {
    availableAfterStep: Steps
    name: String
}
