import { Component, OnDestroy, OnInit, TemplateRef } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { Router } from "@angular/router";
import { Mode } from "@core/enums/mode";
import { Steps } from "@core/enums/steps";
import { LockedInformation, StateManagementService } from "@core/services/state-management.service";
import { TitleService } from "@core/services/title-service.service";
import { FileService } from "@features/data-upload/services/file.service";
import { Delimiter, LineEnding, QuoteChar } from "@shared/model/csv-file-configuration";
import { DataConfigurationEstimation } from "@shared/model/data-configuration";
import { FileConfiguration, FileType } from "@shared/model/file-configuration";
import { FileInformation } from "@shared/model/file-information";
import { ImportPipeData } from "@shared/model/import-pipe-data";
import { Status } from "@shared/model/status";
import { AppConfig, AppConfigService } from "@shared/services/app-config.service";
import { ConfigurationService } from "@shared/services/configuration.service";
import { DataConfigurationService } from "@shared/services/data-configuration.service";
import { DataService } from "@shared/services/data.service";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { LoadingService } from "@shared/services/loading.service";
import { StatusService } from "@shared/services/status.service";
import { combineLatest, Observable } from "rxjs";

@Component({
    selector: "app-upload-file",
    templateUrl: "./upload-file.component.html",
    styleUrls: ["./upload-file.component.less"],
    standalone: false,
})
export class UploadFileComponent implements OnInit, OnDestroy {
    protected readonly FileType = FileType;
    protected readonly Mode = Mode;
    protected readonly Steps = Steps;

    protected configurationFile: File | null = null;
    protected dataFile: File | null = null;
    public fileConfiguration: FileConfiguration;
    protected fhirResourceTypes: string[] = [];
    protected loadingEstimation: boolean = false;

    protected pageData$: Observable<{
        appConfig: AppConfig;
        fileInfo: FileInformation;
        locked: LockedInformation;
        status: Status;
    }>;

    public lineEndings = Object.values(LineEnding);
    public lineEndingLabels: Record<LineEnding, string> = {
        [LineEnding.CR]: "CR (\\r)",
        [LineEnding.CRLF]: "CRLF (\\r\\n)",
        [LineEnding.LF]: "LF (\\n)",
    };

    public delimiters = Object.values(Delimiter);
    public delimiterLabels: Record<Delimiter, string> = {
        [Delimiter.COMMA]: "Comma (,)",
        [Delimiter.SEMICOLON]: "Semicolon (;)",
    };

    public quoteChars = Object.values(QuoteChar);
    public quoteCharLabels: Record<QuoteChar, string> = {
        [QuoteChar.DOUBLE_QUOTE]: "Double Quote (\")",
        [QuoteChar.SINGLE_QUOTE]: "Single Quote (')",
    };

    constructor(
        private readonly appConfigService: AppConfigService,
        private titleService: TitleService,
        private statusService: StatusService,
        private dataService: DataService,
        public dataConfigurationService: DataConfigurationService,
        private router: Router,
        protected fileService: FileService,
        public dialog: MatDialog,
        public loadingService: LoadingService,
        private configurationService: ConfigurationService,
        private readonly errorHandlingService: ErrorHandlingService,
        private readonly stateManagementService: StateManagementService,
    ) {
        this.titleService.setPageTitle("Upload data");
        this.fileConfiguration = fileService.getFileConfiguration();
    }

    public ngOnDestroy(): void {
        this.fileService.setFileConfiguration(this.fileConfiguration)
    }

    public ngOnInit(): void {
        this.pageData$ = combineLatest({
            appConfig: this.appConfigService.appConfig$,
            fileInfo: this.fileService.fileInfo$,
            locked: this.stateManagementService.currentStepLocked$,
            status: this.statusService.status$,
        });
    }

    /**
     * Checks if the data file input is invalid.
     * @return If the data file input is invalid.
     * @protected
     */
    protected get isDataFileInvalid(): boolean {
        return this.dataFile == null;
    }

    /**
     * Checks if the current file configuration {@link #fileConfiguration} is invalid.
     * @return true if the file configuration is invalid.
     * @private
     */
    protected isFileConfigurationInvalid(): boolean {
        if (this.fileConfiguration.fileType === FileType.FHIR) {
            if (this.fileConfiguration.fhirFileConfiguration.resourceType == null) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if the configuration file input is invalid.
     * @return If the configuration file input is invalid.
     * @protected
     */
    protected get isConfigFileInvalid(): boolean {
        return this.configurationFile == null;
    }

    /**
     * Checks if any file input is invalid.
     * @return If any file input is invalid.
     * @protected
     */
    protected get isInvalid(): boolean {
        const stepCompleted = this.statusService.isStepCompleted(Steps.UPLOAD);
        if (stepCompleted) {
            return (this.isDataFileInvalid || this.isFileConfigurationInvalid()) && this.isConfigFileInvalid;
        } else {
            return this.isDataFileInvalid || this.isFileConfigurationInvalid();
        }
    }

    /**
     * Callback for file inputs.
     * Estimates the file configuration for the new file.
     *
     * @param files File list from the input event.
     * @protected
     */
    protected onFileInput(files: FileList | null): void {
        if (files) {
            const file = files[0];
            this.dataFile = file;

            this.loadingEstimation = true;
            this.fileService.estimateFileConfiguration(file).subscribe({
                next: (value) => {
                    this.fileConfiguration = value.estimation;
                    if (value.fhirResourceTypes != null) {
                        this.fhirResourceTypes = value.fhirResourceTypes;
                    }

                    this.loadingEstimation = false;
                },
                error: (e) => {
                    this.handleError(e, "Failed to estimate the file configuration");

                    const fileExtension = this.getFileExtension(file);
                    if (fileExtension != null) {
                        this.setFileType(fileExtension);
                    }

                    this.loadingEstimation = false;
                },
            });
        }
    }

    private getFileExtension(file: File): string | null {
        const fileExtension = file.name.split(".").pop();
        if (fileExtension != undefined) {
            return fileExtension;
        } else {
            return null;
        }
    }

    protected onDataConfigurationFileInput(files: FileList | null) {
        if (files) {
            this.configurationFile = files[0];
        }
    }

    protected uploadFile() {
        const stepCompleted = this.statusService.isStepCompleted(Steps.UPLOAD);

        if (!stepCompleted || !this.isDataFileInvalid) {
            if (!this.dataFile) {
                return;
            }

            this.loadingService.setLoadingStatus(true);

            this.fileService.uploadFile(this.dataFile, this.fileConfiguration).subscribe({
                next: value => {
                    this.fileService.invalidateCache();
                    if (this.configurationFile == null) {
                        // Estimate data configuration based on the data set
                        this.dataService.estimateData().subscribe({
                            next: (d) => this.handleUpload(d),
                            error: (e) => this.handleError(e, "Failed to estimate the data configuration"),
                        });
                    } else {
                        // Use data configuration from the selected file
                        this.configurationService.uploadAllConfigurations(this.configurationFile, [this.dataConfigurationService.CONFIGURATION_NAME]).subscribe(
                            {
                                next: result => {
                                    this.handleConfigurationUpload(result);
                                },
                                error: err => {
                                    this.handleError(err, "Failed to import data configuration");
                                },
                            });
                    }
                },
                error: err => {
                    this.handleError(err, "Failed to upload file");
                },
            });


        } else if (stepCompleted && !this.isConfigFileInvalid) {
            // Only upload the configuration file
            if (!this.configurationFile) {
                return;
            }

            this.loadingService.setLoadingStatus(true);

            this.configurationService.uploadAllConfigurations(this.configurationFile, [this.dataConfigurationService.CONFIGURATION_NAME]).subscribe(
                {
                    next: result => {
                        this.handleConfigurationUpload(result);
                    },
                    error: err => {
                        this.handleError(err, "Failed to import data configuration");
                    },
                });
        }
    }

    /**
     * Handles the result of the configuration upload.
     * Redirects to the next step if the upload was successful, handles the errors otherwise.
     * @param result The result.
     */
    private handleConfigurationUpload(result: ImportPipeData[] | null) {
        if (result === null || result.length === 0) {
            this.handleError("An unexpected error occurred!");
        } else if (result[0].error !== null) {
            // Only one configuration is imported as defined when calling the upload
            this.handleError(result[0].error);
        } else {
            // No error occurred
            this.navigateToNextStep();
        }
    }

    private setFileType(fileExtension: string) {
        switch (fileExtension) {
            case "csv":
                this.fileConfiguration.fileType = FileType.CSV;
                break;
            case "json":
                this.fileConfiguration.fileType = FileType.FHIR;
                break;
            case "xlsx":
                this.fileConfiguration.fileType = FileType.XLSX;
                break;
        }
    }

    protected formatMaxFileSize(maxFileSize: number): string {
        if (maxFileSize < 1024) {
            return maxFileSize + " byte";
        } else if (maxFileSize < 1024 * 1024) {
            return (maxFileSize / 1024).toFixed(2) + " kilobyte";
        } else if (maxFileSize < 1024 * 1024 * 1024) {
            return (maxFileSize / (1024 * 1024)).toFixed(2) + " megabyte";
        } else {
            return (maxFileSize / (1024 * 1024 * 1024)).toFixed(2) + " gigabyte";
        }
    }

    protected openDialog(templateRef: TemplateRef<any>) {
        this.dialog.open(templateRef, {
            width: '60%'
        });
    }

    /**
     * Sets the result of the estimation in the service and navigates to the data configuration.
     * @param estimation The estimation result.
     * @private
     */
    private handleUpload(estimation: DataConfigurationEstimation): void {
        this.dataConfigurationService.setDataConfiguration(estimation.dataConfiguration);
        this.dataConfigurationService.confidence = estimation.confidences
        this.navigateToNextStep();
    }

    private navigateToNextStep() {
        this.loadingService.setLoadingStatus(false);
        this.router.navigateByUrl("/dataConfiguration");
        this.statusService.updateNextStep(Steps.DATA_CONFIG).subscribe();
    }

    private handleError(err: any, message?: string) {
        this.loadingService.setLoadingStatus(false);
        this.errorHandlingService.addError(err, message);
    }
}
