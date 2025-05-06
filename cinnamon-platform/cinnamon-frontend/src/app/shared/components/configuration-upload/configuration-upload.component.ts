import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ConfigurationService } from 'src/app/shared/services/configuration.service';
import { ImportPipeData } from "../../model/import-pipe-data";
import { ErrorHandlingService } from "../../services/error-handling.service";

@Component({
    selector: 'app-configuration-upload',
    templateUrl: './configuration-upload.component.html',
    styleUrls: ['./configuration-upload.component.less'],
    standalone: false
})
export class ConfigurationUploadComponent {
  /**
   * Registered name of the configuration to upload.
   * If null, all configurations in the selected will be uploaded
   */
  @Input() public configurationName!: string;
  @Input() public disabled: boolean = false;
  @Output() onUpload: EventEmitter<ImportPipeData[] | null> = new EventEmitter();

    protected isFileTypeInvalid: boolean = false;

    constructor(
        private readonly errorHandlingService: ErrorHandlingService,
        private configurationService: ConfigurationService,
    ) {
    }

    /**
     * Handles the file upload event.
     * First checks if a file is selected and if the file type is valid (yaml or yml).
     * Then uploads the selected configuration file.
     * Uses the setConfigCallback function to update the configuration in the application.
     * If configured, stores the configuration under the configured name into the database.
     */
    uploadConfiguration(fileList: FileList | null): void {
        if (fileList === null || fileList.length === 0) {
            return;
        }

        const file = fileList[0];
        const fileExtension = this.getFileExtension(file);

        if (fileExtension == null || !["yaml", "yml"].includes(fileExtension)) {
            this.isFileTypeInvalid = true;
            return;
        } else {
            this.isFileTypeInvalid = false;
        }

        const included = [this.configurationName];

        this.configurationService.uploadAllConfigurations(file, included).subscribe({
            next: result => {
                this.onUpload.emit(result);
            },
            error: error => {
                this.errorHandlingService.addError(error, "Could not upload configuration.");
            },
        });
    }

    /**
     * Extracts the file extension from the given file.
     * @param file The File
     * @return The file extension without `.`.
     * @private
     */
    private getFileExtension(file: File): string | undefined {
        return file.name.split(".").pop();
    }

}
