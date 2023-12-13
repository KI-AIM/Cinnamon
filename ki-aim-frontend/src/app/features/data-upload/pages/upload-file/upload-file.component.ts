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

enum LineEnding {
    CR = "\r",
    CRLF = "\r\n",
    LF = "\n",
}

enum Delimiter {
    COMMA = ",",
    SEMICOLON = ";",
}

@Component({
	selector: "app-upload-file",
	templateUrl: "./upload-file.component.html",
	styleUrls: ["./upload-file.component.less"],
})
export class UploadFileComponent {
	Steps = Steps;

	@ViewChild("uploadErrorModal") errorModal: TemplateRef<NgbModal>;
	@ViewChild("fileForm") fileInput: ElementRef;

    public lineEndings = Object.values(LineEnding);
    public lineEndingLabels: Record<LineEnding, string> = {
        [LineEnding.CR]: "CR (\\r)",
        [LineEnding.CRLF]: "CRLF (\\r\\n)",
        [LineEnding.LF]: "LF (\\n)",
    }

    public delimiters = Object.values(Delimiter);
    public delimiterLabels: Record<Delimiter, string> = {
        [Delimiter.COMMA]: "Comma (,)",
        [Delimiter.SEMICOLON]: "Semicolon (;)",
    }

	constructor(
		private titleService: TitleService,
		public stateManagement: StateManagementService,
		private dataService: DataService,
		public dataConfigurationService: DataConfigurationService,
		private router: Router,
		private modalService: NgbModal,
		private fileService: FileService,
		public dialog: MatDialog
	) {
		this.titleService.setPageTitle("Upload data");
	}

	uploadFile(event: any) {
		const file: File = event.target.files[0];
		if (file) {
			this.fileService.setFile(file);

			this.dataService.estimateData(file, this.fileService.getFileConfiguration()).subscribe({
				next: (d) => this.handleUpload(d),
				error: (e) => this.handleError(e),
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
		this.router.navigateByUrl("/dataConfiguration");
		this.stateManagement.addCompletedStep(Steps.UPLOAD);
	}

	private handleError(error: HttpErrorResponse) {
		this.showErrorDialog(error);
	}

	private showErrorDialog(error: HttpErrorResponse) {
		this.dialog.open(InformationDialogComponent, {
			data: {
				title: "An unexpected error occured",
				content: "We are sorry, something went wrong: " +
							"<div class='pre-wrapper'>" +
								"<pre>" + error.message + "</pre>\n" +
								"<pre>" + error.error + "</pre>" +
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
