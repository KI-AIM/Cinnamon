import { Component, ElementRef, TemplateRef, ViewChild } from "@angular/core";
import { Steps } from "src/app/core/enums/steps";
import { StateManagementService } from "src/app/core/services/state-management.service";
import { TitleService } from "src/app/core/services/title-service.service";
import { DataService } from "src/app/shared/services/data.service";
import { plainToClass } from "class-transformer";
import { DataConfiguration } from "../../../../shared/model/data-configuration";
import { DataConfigurationService } from "src/app/shared/services/data-configuration.service";
import { Router } from "@angular/router";
import { HttpErrorResponse } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { FileService } from "../../services/file.service";
import { MatDialog } from "@angular/material/dialog";
import { InformationDialogComponent } from "src/app/shared/components/information-dialog/information-dialog.component";
import { FileConfiguration } from "src/app/shared/model/file-configuration";
import { Delimiter, LineEnding, QuoteChar } from "src/app/shared/model/csv-file-configuration";
import { LoadingService } from "src/app/shared/services/loading.service";
import { ConfigurationService } from "../../../../shared/services/configuration.service";

@Component({
	selector: "app-upload-file",
	templateUrl: "./upload-file.component.html",
	styleUrls: ["./upload-file.component.less"],
})
export class UploadFileComponent {
	Steps = Steps;
    private dataConfigurationFile: File | null;
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
            this.dataConfigurationFile = files[0];
        }
    }

	uploadFile() {
		this.loadingService.setLoadingStatus(true);

        if (!this.dataFile) {
            return;
        }

        this.fileService.setFile(this.dataFile);
        this.fileService.setFileConfiguration(this.fileConfiguration)

        if (this.dataConfigurationFile == null) {
            this.dataService.estimateData(this.dataFile, this.fileService.getFileConfiguration()).subscribe({
                next: (d) => this.handleUpload(d),
                error: (e) => this.handleError(e),
            });
        } else {
            // this.configurationService.uploadAllConfigurations(this.dataConfigurationFile, null, (a) => {
            // }, () => {
            //
            // });
            this.dataConfigurationService.validateConfiguration(
                this.dataConfigurationFile,
                {
                    next: value => {
                        this.handleUpload(value);
                    },
                    error: (e) => this.handleError(e),
                }
            );
        }

	}

    openDialog(templateRef: TemplateRef<any>) {
        this.dialog.open(templateRef, {
            width: '60%'
        });
    }

	private handleUpload(data: Object) {
		this.loadingService.setLoadingStatus(false);
		this.dataConfigurationService.setDataConfiguration(
			plainToClass(DataConfiguration, data)
		);
		this.router.navigateByUrl("/dataConfiguration");
		this.stateManagement.addCompletedStep(Steps.UPLOAD);
	}

	private handleError(error: HttpErrorResponse) {
		this.loadingService.setLoadingStatus(false);
		this.showErrorDialog(error);
	}

	private showErrorDialog(error: HttpErrorResponse) {
        let errorMessage = "";
        if (error.error.hasOwnProperty("errors")) {
            errorMessage = JSON.stringify(error.error.errors, null, 2);
        } else {
            errorMessage = error.error;
        }

		this.dialog.open(InformationDialogComponent, {
			data: {
				title: "An unexpected error occurred",
				content: "We are sorry, something went wrong: " +
							"<div class='pre-wrapper'>" +
								"<pre>" + error.message + "</pre>\n" +
								"<pre>" + errorMessage + "</pre>" +
							"</div>" +
							"<b>Please try again with a different file!</b>"
			}
		});
	}

	closeErrorModal() {
		this.modalService.dismissAll();
		this.fileInput.nativeElement.value = "";
	}
}
