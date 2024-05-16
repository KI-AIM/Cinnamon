import { Component, ElementRef, TemplateRef, ViewChild } from "@angular/core";
import { Steps } from "src/app/core/enums/steps";
import { StateManagementService } from "src/app/core/services/state-management.service";
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
import { FileConfiguration } from "src/app/shared/model/file-configuration";
import { Delimiter, LineEnding, QuoteChar } from "src/app/shared/model/csv-file-configuration";
import { LoadingService } from "src/app/shared/services/loading.service";
import { ConfigurationService } from "../../../../shared/services/configuration.service";
import { ErrorMessageService } from "src/app/shared/services/error-message.service";

@Component({
	selector: "app-upload-file",
	templateUrl: "./upload-file.component.html",
	styleUrls: ["./upload-file.component.less"],
})
export class UploadFileComponent {
	Steps = Steps;
    private configurationFile: File | null;
	protected dataFile: File | null;
	public fileConfiguration: FileConfiguration;

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
		public stateManagement: StateManagementService,
		private dataService: DataService,
		public dataConfigurationService: DataConfigurationService,
		private router: Router,
		private modalService: NgbModal,
		private fileService: FileService,
		public dialog: MatDialog,
		public loadingService: LoadingService,
        private configurationService: ConfigurationService,
		private errorMessageService: ErrorMessageService,
	) {
		this.titleService.setPageTitle("Upload data");
		this.fileConfiguration = fileService.getFileConfiguration();
	}

	onFileInput(event: Event) {
		const files = (event.target as HTMLInputElement)?.files;

		if (files) {
			this.dataFile = files[0];
		}
	}

    onDataConfigurationFileInput(event: Event) {
        const files = (event.target as HTMLInputElement)?.files;

        if (files) {
            this.configurationFile = files[0];
        }
    }

	uploadFile() {
		this.loadingService.setLoadingStatus(true);

        if (!this.dataFile) {
            return;
        }

        this.fileService.setFile(this.dataFile);
        this.fileService.setFileConfiguration(this.fileConfiguration)

        if (this.configurationFile == null) {
            this.dataService.estimateData(this.dataFile, this.fileService.getFileConfiguration()).subscribe({
                next: (d) => this.handleUpload(d),
                error: (e) => this.handleError("Failed to estimate the data types" + this.errorMessageService.convertResponseToMessage(e)),
            });
        } else {
            this.configurationService.uploadAllConfigurations(this.configurationFile, null).subscribe(result => {
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
			});
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
		this.stateManagement.addCompletedStep(Steps.UPLOAD);
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
}
