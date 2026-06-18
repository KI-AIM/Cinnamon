import { Platform } from "@angular/cdk/platform";
import { Component, OnDestroy, OnInit, TemplateRef, ViewChild } from "@angular/core";
import { AbstractControl, FormBuilder, FormGroup, ValidationErrors, ValidatorFn, Validators } from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";
import { Router } from "@angular/router";
import { Mode } from "@core/enums/mode";
import { Steps } from "@core/enums/steps";
import { LockedInformation, StateManagementService } from "@core/services/state-management.service";
import { TitleService } from "@core/services/title-service.service";
import { FileService } from "@features/data-upload/services/file.service";
import { Delimiter, LineEnding, QuoteChar } from "@shared/model/csv-file-configuration";
import { DataConfigurationEstimation } from "@shared/model/data-configuration";
import {
    DataSourceConfiguration,
    DataSourceType,
    FileConfiguration,
    FileConfigurationEstimation,
    FileType
} from "@shared/model/file-configuration";
import { FileInformation } from "@shared/model/file-information";
import { Status } from "@shared/model/status";
import { AppConfig, AppConfigService } from "@shared/services/app-config.service";
import { ConfigurationService } from "@shared/services/configuration.service";
import { DataConfigurationService } from "@shared/services/data-configuration.service";
import { DataService } from "@shared/services/data.service";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { LoadingService } from "@shared/services/loading.service";
import { StatusService } from "@shared/services/status.service";
import { combineLatest, Observable, switchMap, take, tap } from "rxjs";

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

    protected dataSourceConfigurationForm: FormGroup;

    protected isDataFileStored: boolean = false;

    protected configurationFile: File | null = null;
    protected dataFile: File | null = null;
    public fileConfiguration: FileConfiguration;
    protected fhirResourceTypes: string[] = [];
    protected loadingEstimation: boolean = false;

    protected pageData$: Observable<{
        appConfig: AppConfig;
        dataSourceConfig: DataSourceConfiguration;
        fileConfiguration: FileConfiguration;
        fileInfo: FileInformation;
        locked: LockedInformation;
        status: Status;
    }>;

    @ViewChild("fileConfigurationDialog") private fileConfigurationDialog!: TemplateRef<MatDialog>;

    public lineEndings = Object.values(LineEnding);
    public lineEndingLabels: Record<LineEnding, string> = {
        [LineEnding.CR]: "CR (\\r)",
        [LineEnding.CRLF]: "CRLF (\\r\\n)",
        [LineEnding.LF]: "LF (\\n)",
    };
    public lineEndingOs: Record<LineEnding, string> = {
        [LineEnding.CR]: "Older macOS",
        [LineEnding.CRLF]: "Windows",
        [LineEnding.LF]: "Unix (Linux, macOS)",
    }

    public delimiters = Object.values(Delimiter);
    public delimiterLabels: Record<Delimiter, string> = {
        [Delimiter.COMMA]: "Comma (,)",
        [Delimiter.SEMICOLON]: "Semicolon (;)",
    };
    public delimiterExamples: Record<Delimiter, string> = {
        [Delimiter.COMMA]: "PID,name,age\n123656,John,36",
        [Delimiter.SEMICOLON]: "PID;name;age\n123656;John;36",
    };

    public quoteChars = Object.values(QuoteChar);
    public quoteCharLabels: Record<QuoteChar, string> = {
        [QuoteChar.DOUBLE_QUOTE]: "Double Quote (\")",
        [QuoteChar.SINGLE_QUOTE]: "Single Quote (')",
    };
    public quoteCharExamples: Record<QuoteChar, string> = {
        [QuoteChar.DOUBLE_QUOTE]: "\"PID\",\"name\",\"age\"\n\"123456\",\"John\",36",
        [QuoteChar.SINGLE_QUOTE]: "'PID','name','age'\n'123456','John',36",
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
        private readonly formBuilder: FormBuilder,
        protected readonly platform: Platform,
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
            dataSourceConfig: this.fileService.dataSourceConfiguration$,
            fileConfiguration: this.fileService.fileConfiguration$,
            fileInfo: this.fileService.fileInfo$,
            locked: this.stateManagementService.currentStepLocked$,
            status: this.statusService.statusNonNull$,
        }).pipe(
            tap((data) => {
                this.isDataFileStored = data.fileInfo.name != null;

                if (data.dataSourceConfig == null) {
                    data.dataSourceConfig = new DataSourceConfiguration(DataSourceType.LOCAL, null);
                }
                this.createDataSourceConfigurationForm(data.dataSourceConfig);

                if (data.fileConfiguration != null) {
                    this.fileConfiguration = data.fileConfiguration;
                }

                if (data.fileInfo.fhirResourceTypes != null) {
                    this.fhirResourceTypes = data.fileInfo.fhirResourceTypes;
                }
            }),
        );
    }

    /**
     * Checks if the data source configuration form is invalid.
     * @return If the data source configuration form is invalid.
     * @protected
     */
    protected get isDataSourceInvalid(): boolean {
        return this.dataSourceConfigurationForm.invalid;
    }

    protected get isDataSourceTypeInvalid(): boolean {
        return this.dataSourceConfigurationForm.get('dataSourceType')?.invalid ?? false;
    }

    /**
     * Checks if the data file input is invalid.
     * @return If the data file input is invalid.
     * @protected
     */
    protected get isDataFileInvalid(): boolean {
        return !this.isDataFileStored && this.dataFile == null;
    }

    /**
     * Checks if the current file configuration {@link #fileConfiguration} is invalid.
     * @return true if the file configuration is invalid.
     * @private
     */
    protected isFileConfigurationInvalid(): boolean {
        if (this.fileConfiguration.fileType === FileType.FHIR) {
            if (this.fileConfiguration.fhirFileConfiguration == null || this.fileConfiguration.fhirFileConfiguration.resourceType == null) {
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
            return this.isFileConfigurationInvalid() && this.isConfigFileInvalid;
        } else {
            return this.isFileConfigurationInvalid();
        }
    }

    /**
     * Callback for file inputs.
     * Estimates the file configuration for the new file.
     *
     * @param files File list from the input event.
     * @param showFileConfig Whether to show the file configuration dialog after the estimation finished.
     * @protected
     */
    protected onFileInput(files: FileList | null, showFileConfig: boolean): void {
        if (files) {
            const file = files[0];
            this.dataFile = file;

            this.loadingEstimation = true;

            const config = this.dataSourceConfigurationForm.getRawValue();
            this.fileService.uploadDataSourceConfiguration(config).pipe(
                switchMap(() => this.fileService.uploadFile(file)),
                switchMap(() => this.statusService.updateNextStep(Steps.UPLOAD)),
                switchMap(() => this.fileService.dataSourceConfiguration$),
                take(1),
                switchMap(() => this.fileService.estimateFileConfiguration())
            ).subscribe({
                next: (value) => {
                    this.handleFileConfigurationEstimation(value, showFileConfig);
                },
                error: (e) => {
                    this.handleFileConfigurationEstimationError(e, file)
                },
            });
        }
    }

    protected fetchDataFile(showFileConfig: boolean): void {
        this.loadingEstimation = true;

        const config = this.dataSourceConfigurationForm.getRawValue();
        this.fileService.uploadDataSourceConfiguration(config).pipe(
            switchMap(() => this.statusService.updateNextStep(Steps.UPLOAD)),
            switchMap(() => this.fileService.retrieveFile(this.fileConfiguration)),
            switchMap(() => this.fileService.estimateFileConfiguration()),
        ).subscribe({
            next: (value) => {
                this.handleFileConfigurationEstimation(value, showFileConfig);
            },
            error: (e) => {
                this.handleFileConfigurationEstimationError(e, null)
            },
        });
    }

    private createDataSourceConfigurationForm(initialValue: DataSourceConfiguration): void {
        this.dataSourceConfigurationForm = this.formBuilder.group({
            dataSourceType: [
                {value: initialValue.dataSourceType, disabled: this.locked},
                {validators: [Validators.required]}
            ],
            server: this.formBuilder.group({
                url: [
                    {value: initialValue.server?.url, disabled: this.locked},
                    {validators: [Validators.required]}
                ],
            }, {validators: [this.validateDataSourceUrl()]}),
        });
    }

    /**
     * Returns a validator function that validates the data source URL.
     * @return A validator function that validates the data source URL.
     * @private
     */
    private validateDataSourceUrl(): ValidatorFn {
        return (control: AbstractControl): ValidationErrors | null => {
            const dataSourceTypeControl = control.get('dataSourceType');

            const dataSourceType = dataSourceTypeControl?.value as DataSourceType | null;

            if (dataSourceType == null || dataSourceType === DataSourceType.LOCAL) {
                // No further constraints apply
                return null;
            }

            if (dataSourceType == DataSourceType.FHIR_SERVER) {
                const serverUrlControl = control.get('server')?.get('url')!;
                const serverUrl = serverUrlControl.value as string;

                const error = (serverUrl == null || serverUrl.trim().length === 0)
                    ? {serverUrl: true}
                    : null;

                serverUrlControl.setErrors(error);
                serverUrlControl.markAsTouched();
            }

            return null;
        }
    }

    protected get dataSourceType(): DataSourceType {
        return this.dataSourceConfigurationForm.get("dataSourceType")?.value;
    }

    private getFileExtension(file: File): string | null {
        const fileExtension = file.name.split(".").pop();
        if (fileExtension != undefined) {
            return fileExtension;
        } else {
            return null;
        }
    }

    /**
     * Checks if the user's OS uses the given line ending.
     * @param lineEnding
     */
    protected isUserOs(lineEnding: LineEnding): boolean {
        const platform = window.navigator.platform;
        const crlf = ["Win32"];
        const cr = ["darwin"];
        const lf = ["linux"];

        switch (lineEnding) {
            case LineEnding.CRLF:
                return crlf.includes(platform);
            case LineEnding.LF:
                return lf.includes(platform);
            case LineEnding.CR:
                return cr.includes(platform);
            default:
                return false;
        }
    }

    protected hasCurrentFileHeader(): boolean {
        if (this.fileConfiguration.fileType === FileType.CSV && this.fileConfiguration.csvFileConfiguration != null) {
            return this.fileConfiguration.csvFileConfiguration.hasHeader;
        } else if (this.fileConfiguration.fileType === FileType.XLSX && this.fileConfiguration.xlsxFileConfiguration != null) {
            return this.fileConfiguration.xlsxFileConfiguration.hasHeader;
        }
        return false;
    }

    protected onDataConfigurationFileInput(files: FileList | null) {
        if (files) {
            this.configurationFile = files[0];
        }
    }

    protected uploadFile() {
        this.loadingService.setLoadingStatus(true);

        this.fileService.uploadFileConfiguration(this.fileConfiguration).subscribe({
            next: () => {
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
                            next: () => {
                                this.handleConfigurationUpload();
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
    }

    /**
     * Handles the result of the configuration upload.
     * Redirects to the next step if the upload was successful, handles the errors otherwise.
     */
    private handleConfigurationUpload() {
        this.navigateToNextStep();
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

    private handleFileConfigurationEstimation(value: FileConfigurationEstimation, openDialog: boolean) {
        this.fileConfiguration = value.estimation;

        this.loadingEstimation = false;

        if (openDialog && value.estimation.fileType === FileType.FHIR) {
            this.openDialog(this.fileConfigurationDialog);
        }
    }

    private handleFileConfigurationEstimationError(err: any, file: File | null) {
        this.handleError(err, "Failed to estimate the file configuration");

        if (file != null) {
            const fileExtension = this.getFileExtension(file);
            if (fileExtension != null) {
                this.setFileType(fileExtension);
            }
        }

        this.loadingEstimation = false;
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

    protected get locked(): boolean {
        return this.statusService.isStepCompleted(Steps.VALIDATION)
    }

    protected readonly DataSourceType = DataSourceType;
}
