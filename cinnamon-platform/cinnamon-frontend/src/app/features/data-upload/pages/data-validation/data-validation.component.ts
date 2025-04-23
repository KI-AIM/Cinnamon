import {Component, OnInit, TemplateRef } from "@angular/core";
import { LoadingService } from "src/app/shared/services/loading.service";
import { Router } from "@angular/router";
import { DataService } from "src/app/shared/services/data.service";
import { Steps } from "src/app/core/enums/steps";
import { TitleService } from "src/app/core/services/title-service.service";
import { MatDialog } from "@angular/material/dialog";
import {DataSetInfoService} from "../../services/data-set-info.service";
import { map, Observable, switchMap } from "rxjs";
import { StatusService } from "../../../../shared/services/status.service";
import {FileService} from "../../services/file.service";
import { ConfigurationService } from "../../../../shared/services/configuration.service";
import { ErrorHandlingService } from "../../../../shared/services/error-handling.service";

@Component({
    selector: "app-data-validation",
    templateUrl: "./data-validation.component.html",
    styleUrls: ["./data-validation.component.less"],
    standalone: false
})
export class DataValidationComponent implements OnInit {
    protected numberRows$: Observable<number>;
    protected numberInvalidRows$: Observable<number>;

	constructor(
		private loadingService: LoadingService,
		private router: Router,
        private statusService: StatusService,
        private readonly configurationService: ConfigurationService,
        protected dataSetInfoService: DataSetInfoService,
        private readonly fileService: FileService,
		private dataService: DataService,
		private titleService: TitleService,
        private dialog: MatDialog,
        private errorHandlingService: ErrorHandlingService,
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
            error: (e) => this.errorHandlingService.addError(e),
        });
	}

    protected deleteData() {
        this.dataService.deleteData().pipe(
                switchMap(() => {
                    return this.statusService.updateNextStep(Steps.UPLOAD);
                }),
            ).subscribe({
            next: () => {
                this.configurationService.invalidateCache();
                this.fileService.invalidateCache();
                this.dataSetInfoService.invalidateCache();
                this.router.navigateByUrl("/upload");
            }
        });
    }

	private handleConfirm() {
		this.loadingService.setLoadingStatus(false);
		this.router.navigateByUrl("/anonymizationConfiguration");
	}
}
