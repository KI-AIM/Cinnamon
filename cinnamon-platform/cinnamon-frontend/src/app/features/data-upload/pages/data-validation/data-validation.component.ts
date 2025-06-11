import { Component, OnInit, TemplateRef } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { Router } from "@angular/router";
import { Mode } from "@core/enums/mode";
import { Steps } from "@core/enums/steps";
import { TitleService } from "@core/services/title-service.service";
import { DataSetInfoService } from "@features/data-upload/services/data-set-info.service";
import { FileService } from "@features/data-upload/services/file.service";
import { DataSetInfo } from "@shared/model/data-set-info";
import { Status } from "@shared/model/status";
import { ConfigurationService } from "@shared/services/configuration.service";
import { DataService } from "@shared/services/data.service";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { LoadingService } from "@shared/services/loading.service";
import { StatusService } from "@shared/services/status.service";
import { Observable, switchMap } from "rxjs";

@Component({
    selector: "app-data-validation",
    templateUrl: "./data-validation.component.html",
    styleUrls: ["./data-validation.component.less"],
    standalone: false
})
export class DataValidationComponent implements OnInit {
    protected readonly Mode = Mode;

    protected dataSetInfo$: Observable<DataSetInfo>;
    protected status$: Observable<Status>;

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
        this.dataSetInfo$ = this.dataSetInfoService.getDataSetInfoOriginal$();
        this.status$ = this.statusService.status$;
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

    protected readonly Steps = Steps;
}
