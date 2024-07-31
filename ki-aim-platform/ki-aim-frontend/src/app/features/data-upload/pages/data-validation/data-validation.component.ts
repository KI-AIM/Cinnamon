import { Component } from "@angular/core";
import { TransformationService } from "../../services/transformation.service";
import { LoadingService } from "src/app/shared/services/loading.service";
import { Router } from "@angular/router";
import { StateManagementService } from "src/app/core/services/state-management.service";
import { DataService } from "src/app/shared/services/data.service";
import { FileService } from "../../services/file.service";
import { HttpErrorResponse } from "@angular/common/http";
import { Steps } from "src/app/core/enums/steps";
import { DataConfigurationService } from "src/app/shared/services/data-configuration.service";
import { TitleService } from "src/app/core/services/title-service.service";
import { MatDialog } from "@angular/material/dialog";
import { InformationDialogComponent } from "src/app/shared/components/information-dialog/information-dialog.component";
import { ErrorMessageService } from "src/app/shared/services/error-message.service";

@Component({
	selector: "app-data-validation",
	templateUrl: "./data-validation.component.html",
	styleUrls: ["./data-validation.component.less"],
})
export class DataValidationComponent {
	constructor(
		public transformationService: TransformationService,
		private loadingService: LoadingService,
		private router: Router,
		private stateManagement: StateManagementService,
		private dataService: DataService,
		private fileService: FileService,
		private configuration: DataConfigurationService,
		private titleService: TitleService,
        private dialog: MatDialog,
		private errorMessageService: ErrorMessageService,
	) {
        this.titleService.setPageTitle("Data validation");
    }

    protected get locked(): boolean {
        return this.stateManagement.isStepCompleted(Steps.VALIDATION);
    }

	uploadData() {
		this.loadingService.setLoadingStatus(true);

		this.dataService
			.storeData(
				this.fileService.getFile(),
				this.configuration.getDataConfiguration(),
				this.fileService.getFileConfiguration()
			)
			.subscribe({
				next: (d) => this.handleUpload(d),
				error: (e) => this.handleError(e),
			});
	}

	private handleUpload(data: Object) {
		this.loadingService.setLoadingStatus(false);

		this.router.navigateByUrl("/anonymizationConfiguration");
        this.stateManagement.setNextStep(Steps.ANONYMIZATION_CONFIG)
	}

	private handleError(error: HttpErrorResponse) {
		this.loadingService.setLoadingStatus(false);

		this.dialog.open(InformationDialogComponent, {
			data: {
				title: "An error occurred",
				content: "We are sorry, something went wrong: " + this.errorMessageService.convertResponseToMessage(error),
			}
		});
	}
}
