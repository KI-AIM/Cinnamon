import { Component, EventEmitter, Input, Output, TemplateRef } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ConfigurationService } from 'src/app/shared/services/configuration.service';

@Component({
  selector: 'app-configuration-upload',
  templateUrl: './configuration-upload.component.html',
  styleUrls: ['./configuration-upload.component.less']
})
export class ConfigurationUploadComponent {
  protected error: string;

  /**
   * Registered name of the configuration to upload.
   * If null, all configurations in the selected will be uploaded
   */
  @Input() public configurationName: string | null = null;
  @Output() onError: EventEmitter<any> = new EventEmitter();

  constructor(
    private configurationService: ConfigurationService,
    private dialog: MatDialog,
  ) {
    this.error = "";
  }

  /**
   * Opens the import dialog.
   * @param templateRef Reference to the dialog.
   */
  openDialog(templateRef: TemplateRef<any>) {
    this.dialog.open(templateRef, {
      width: '60%'
    });
  }

  /**
   * Closes the import dialog.
   */
  private closeDialog() {
    this.dialog.closeAll();
  }

  /**
   * Uploads the selected configuration file.
   * If the configuration name of this component is set, only this configuration will be uploaded.
   * Uses the setConfigCallback function to update the configuration in the application.
   * If configured, stores the configuration under the configured name into the database.
   */
  uploadConfiguration() {
    const files = (document.getElementById("configInput") as HTMLInputElement).files;
    if (!files || files.length === 0) {
      this.error = "Please select a file!";
      return;
    }

    const included = [];
    if (this.configurationName !== null) {
      included.push(this.configurationName);
    } else {
      for (const config of this.configurationService.getRegisteredConfigurations()) {
        if ((document.getElementById(config.name + "-input") as HTMLInputElement).checked) {
          included.push(config.name);
        }
      }
    }

    const errorCallback = (error: string) => this.onError.emit([error]);
    this.configurationService.uploadAllConfigurations(files[0], included, errorCallback, () => this.closeDialog());
  }

}
