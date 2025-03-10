import {Component, OnInit, TemplateRef } from "@angular/core";
import { LoadingService } from "src/app/shared/services/loading.service";
import { Router } from "@angular/router";
import { DataService } from "src/app/shared/services/data.service";
import { HttpErrorResponse } from "@angular/common/http";
import { Steps } from "src/app/core/enums/steps";
import { TitleService } from "src/app/core/services/title-service.service";
import { MatDialog } from "@angular/material/dialog";
import { InformationDialogComponent } from "src/app/shared/components/information-dialog/information-dialog.component";
import { ErrorMessageService } from "src/app/shared/services/error-message.service";
import {DataSetInfoService} from "../../services/data-set-info.service";
import { map, Observable, switchMap } from "rxjs";
import { StatusService } from "../../../../shared/services/status.service";
import {FileService} from "../../services/file.service";

@Component({
	selector: "app-data-validation",
	templateUrl: "./data-validation.component.html",
	styleUrls: ["./data-validation.component.less"],
})
export class DataValidationComponent implements OnInit {
    protected numberRows$: Observable<number>;
    protected numberInvalidRows$: Observable<number>;

	constructor(
		private loadingService: LoadingService,
		private router: Router,
        private statusService: StatusService,
        protected dataSetInfoService: DataSetInfoService,
        private readonly fileService: FileService,
		private dataService: DataService,
		private titleService: TitleService,
        private dialog: MatDialog,
		private errorMessageService: ErrorMessageService,
	) {
        this.titleService.setPageTitle("Data validation");
    }

    ngOnInit(): void {
        this.numberRows$ = this.dataSetInfoService.getDataSetInfoOriginal$().pipe(
            map(value => {
                return value.numberRows;
            }),
        );
        this.numberInvalidRows$ = this.dataSetInfoService.getDataSetInfoOriginal$().pipe(
            map(value => {
                return value.numberInvalidRows;
            }),
        );
    }

    protected get locked(): boolean {
        return this.statusService.isStepCompleted(Steps.VALIDATION);
    }

    openDeleteDialog(templateRef: TemplateRef<any>) {
        this.dialog.open(templateRef, {
            disableClose: true,
            width: '60%'
        });
    }

	confirmData() {
		this.loadingService.setLoadingStatus(true);

        this.dataService.confirmData().pipe(
            switchMap(() => {
                return this.statusService.updateNextStep(Steps.ANONYMIZATION);
            }),
        ).subscribe({
            next: () => this.handleConfirm(),
            error: (e) => this.handleError(e),
        });
	}

    protected deleteData() {
        this.dataService.deleteData().pipe(
                switchMap(() => {
                    return this.statusService.updateNextStep(Steps.UPLOAD);
                }),
            ).subscribe({
            next: () => {
                this.fileService.invalidateCache();
                this.router.navigateByUrl("/upload");
            }
        });
    }

	private handleConfirm() {
		this.loadingService.setLoadingStatus(false);
		this.router.navigateByUrl("/anonymizationConfiguration");
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
