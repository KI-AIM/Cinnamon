import {Component, ElementRef, OnDestroy, OnInit, TemplateRef, ViewChild} from "@angular/core";
import { Steps } from "src/app/core/enums/steps";
import { TitleService } from "src/app/core/services/title-service.service";
import { DataService } from "src/app/shared/services/data.service";
import { plainToClass } from "class-transformer";
import { DataConfiguration } from "../../../../shared/model/data-configuration";
import { DataConfigurationService } from "src/app/shared/services/data-configuration.service";
import { Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { FileService } from "../../services/file.service";
import { MatDialog } from "@angular/material/dialog";
import { InformationDialogComponent } from "src/app/shared/components/information-dialog/information-dialog.component";
import { FileConfiguration, FileType } from "src/app/shared/model/file-configuration";
import { Delimiter, LineEnding, QuoteChar } from "src/app/shared/model/csv-file-configuration";
import { LoadingService } from "src/app/shared/services/loading.service";
import { ConfigurationService } from "../../../../shared/services/configuration.service";
import { ErrorMessageService } from "src/app/shared/services/error-message.service";
import { ImportPipeData } from "src/app/shared/model/import-pipe-data";
import { StatusService } from "../../../../shared/services/status.service";
import { Observable } from "rxjs";
import { FileInformation } from "../../../../shared/model/file-information";

@Component({
    selector: "app-upload-file",
    templateUrl: "./upload-file.component.html",
    styleUrls: ["./upload-file.component.less"],
    standalone: false
})
export class UploadFileComponent implements OnInit, OnDestroy {
	Steps = Steps;
    private configurationFile: File | null;
	protected dataFile: File | null;
	public fileConfiguration: FileConfiguration;

    protected fileInfo$: Observable<FileInformation>;

    protected isDataFileTypeInvalid: boolean = false;
    protected isConfigFileTypeInvalid: boolean = false;

	@ViewChild("uploadErrorModal") errorModal: TemplateRef<NgbModal>;
	@ViewChild("fileForm") fileInput: ElementRef;

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
		private titleService: TitleService,
        private statusService: StatusService,
		private dataService: DataService,
		public dataConfigurationService: DataConfigurationService,
		private router: Router,
		private modalService: NgbModal,
		protected fileService: FileService,
		public dialog: MatDialog,
		public loadingService: LoadingService,
        private configurationService: ConfigurationService,
		private errorMessageService: ErrorMessageService,
	) {
		this.titleService.setPageTitle("Upload data");
		this.fileConfiguration = fileService.getFileConfiguration();
	}

    ngOnDestroy(): void {
        this.fileService.setFileConfiguration(this.fileConfiguration)
    }

    ngOnInit(): void {
        this.fileInfo$ = this.fileService.fileInfo$;
    }

    /**
     * Checks if the data file input is invalid.
     * @return If the data file input is invalid.
     * @protected
     */
    protected get isDataFileInvalid(): boolean {
        return this.dataFile == null || this.isDataFileTypeInvalid;
    }

    /**
     * Checks if the configuration file input is invalid.
     * @return If the configuration file input is invalid.
     * @protected
     */
    protected get isConfigFileInvalid(): boolean {
        return this.isConfigFileTypeInvalid;
    }

    /**
     * Checks if any file input is invalid.
     * @return If any file input is invalid.
     * @protected
     */
    protected get isInvalid(): boolean {
        return this.isDataFileInvalid || this.isConfigFileInvalid;
    }

    protected get locked(): boolean {
        return this.statusService.isStepCompleted(Steps.VALIDATION);
    }

	onFileInput(event: Event) {
		const files = (event.target as HTMLInputElement)?.files;

		if (files) {
            const fileExtension = this.getFileExtension(files[0]);
            const validFileExtensions = ["csv", "xlsx"];

            if (fileExtension && validFileExtensions.includes(fileExtension)) {
                this.isDataFileTypeInvalid = false;
                this.dataFile = files[0];
                this.setFileType(fileExtension);
            } else {
                this.isDataFileTypeInvalid = true;
            }
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

    onDataConfigurationFileInput(event: Event) {
        const files = (event.target as HTMLInputElement)?.files;

        if (files) {
            const fileExtension = this.getFileExtension(files[0]);
            const validFileExtensions = ["yml", "yaml"];

            if (fileExtension && validFileExtensions.includes(fileExtension)) {
                this.isConfigFileTypeInvalid = false;
                this.configurationFile = files[0];
            } else {
                this.isConfigFileTypeInvalid = true;
            }
        }
    }

	uploadFile() {
		this.loadingService.setLoadingStatus(true);

        if (!this.dataFile) {
            return;
        }

        this.fileService.uploadFile(this.dataFile, this.fileConfiguration).subscribe({
            next: value => {
                this.fileService.invalidateCache();
                if (this.configurationFile == null) {
                    // Estimate data configuration based on the data set
                    this.dataService.estimateData().subscribe({
                        next: (d) => this.handleUpload(d),
                        error: (e) => this.handleError("Failed to estimate the data types" + this.errorMessageService.convertResponseToMessage(e)),
                    });
                } else {
                    // Use data configuration from the selected file
                    this.configurationService.uploadAllConfigurations(this.configurationFile, null).subscribe(result => {
                        this.handleConfigurationUpload(result);
                    });
                }
            },
            error: err => {
                this.handleError("Failed to upload file" + this.errorMessageService.convertResponseToMessage(err));
            },
        });
	}

    /**
     * Handles the result of the configuration upload.
     * Redirects to the next step if the upload was successful, handles the errors otherwise.
     * @param result The result.
     */
    private handleConfigurationUpload(result: ImportPipeData[] | null) {
        let hasError = false;
        let errorMessage = "";

        if (result === null) {
            errorMessage = "An unexpected error occurred!";
            hasError = true;
        } else {
            for (const importData of result) {
                if (importData.error !== null) {
                    hasError = true;
                    errorMessage += "Errors for '" + importData.name + "':" + this.errorMessageService.convertResponseToMessage(importData.error);
                }
            }
        }

        if (hasError) {
            this.handleError("Failed to import the configurations<br>" + errorMessage);
        } else {
            this.navigateToNextStep();
        }
    }

	setFileType(fileExtension: string) {
		switch (fileExtension) {
			case "csv":
				this.fileConfiguration.fileType = FileType.CSV;
				break;
			case "xlsx":
				this.fileConfiguration.fileType = FileType.XLSX;
				break;
		}
	}

    openDialog(templateRef: TemplateRef<any>) {
        this.dialog.open(templateRef, {
            width: '60%'
        });
    }

	private handleUpload(data: Object) {
		this.dataConfigurationService.setDataConfiguration(
			plainToClass(DataConfiguration, data)
		);
		this.navigateToNextStep();
	}

	private navigateToNextStep() {
		this.loadingService.setLoadingStatus(false);
		this.router.navigateByUrl("/dataConfiguration");
        this.statusService.updateNextStep(Steps.DATA_CONFIG).subscribe();
	}

	private handleError(error: string) {
		this.loadingService.setLoadingStatus(false);
		this.showErrorDialog(error);
	}

	private showErrorDialog(error: string) {
		this.dialog.open(InformationDialogComponent, {
			data: {
				title: "An error occurred",
				content: error,
			}
		});
	}

	closeErrorModal() {
		this.modalService.dismissAll();
		this.fileInput.nativeElement.value = "";
	}

    protected readonly FileType = FileType;
}
