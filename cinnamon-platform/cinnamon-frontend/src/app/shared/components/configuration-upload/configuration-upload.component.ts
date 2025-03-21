import { Component, EventEmitter, Input, OnInit, Output, TemplateRef } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ConfigurationService } from 'src/app/shared/services/configuration.service';
import { ImportPipeData } from "../../model/import-pipe-data";
import { Observable } from "rxjs";
import { StepConfiguration } from "../../model/step-configuration";

@Component({
    selector: 'app-configuration-upload',
    templateUrl: './configuration-upload.component.html',
    styleUrls: ['./configuration-upload.component.less'],
    standalone: false
})
export class ConfigurationUploadComponent implements OnInit{
  protected hasFile: boolean = false;

  /**
   * Registered name of the configuration to upload.
   * If null, all configurations in the selected will be uploaded
   */
  @Input() public configurationName: string | null = null;
  @Input() public configurationNameObservable: Observable<StepConfiguration> | null = null;
  @Input() public disabled: boolean = false;
  @Output() onUpload: EventEmitter<ImportPipeData[] | null> = new EventEmitter();


  constructor(
    private configurationService: ConfigurationService,
    private dialog: MatDialog,
  ) {
  }

  ngOnInit() {
      if (this.configurationNameObservable !== null) {
          this.configurationNameObservable.subscribe({
              next: value => {
                  this.configurationName = value.configurationName;
              }
          });
      }
  }

  /**
   * Opens the import dialog.
   * @param templateRef Reference to the dialog.
   */
  openDialog(templateRef: TemplateRef<any>) {
    this.dialog.open(templateRef, {
      width: '60%'
    }).afterClosed().subscribe({
        next: () => {
            this.hasFile = false;
        }
    });
  }

  /**
   * Closes the import dialog.
   */
  public closeDialog() {
    this.dialog.closeAll();
  }

    /**
     * Activates/deactivates the upload button if an input is made.
     * @param event Input event of the file input.
     * @protected
     */
  protected updateUploadButton(event: Event) {
      const input = event.target as HTMLInputElement;
      this.hasFile = input.files != null && input.files.length > 0;
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
      console.error("No file selected. This should be prevented by disabling the upload button.");
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

    this.configurationService.uploadAllConfigurations(files[0], included).subscribe(result => {
        this.onUpload.emit(result);
    });
    this.closeDialog();
  }

}
