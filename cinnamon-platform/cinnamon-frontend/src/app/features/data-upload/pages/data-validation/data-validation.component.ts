import { Component, OnInit } from "@angular/core";
import { Router } from "@angular/router";
import { Mode } from "@core/enums/mode";
import { Steps } from "@core/enums/steps";
import { LockedInformation, StateManagementService } from "@core/services/state-management.service";
import { TitleService } from "@core/services/title-service.service";
import { DataSetInfoService } from "@features/data-upload/services/data-set-info.service";
import { DataSetInfo } from "@shared/model/data-set-info";
import { Status } from "@shared/model/status";
import { DataService } from "@shared/services/data.service";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { LoadingService } from "@shared/services/loading.service";
import { StatusService } from "@shared/services/status.service";
import { combineLatest, Observable, switchMap } from "rxjs";

@Component({
    selector: "app-data-validation",
    templateUrl: "./data-validation.component.html",
    styleUrls: ["./data-validation.component.less"],
    standalone: false
})
export class DataValidationComponent implements OnInit {
    protected readonly Mode = Mode;

    protected pageData$: Observable<{
        dataSetInfo: DataSetInfo;
        locked: LockedInformation;
        status: Status;
    }>;

	constructor(
		private loadingService: LoadingService,
		private router: Router,
        private statusService: StatusService,
        protected dataSetInfoService: DataSetInfoService,
		private dataService: DataService,
		private titleService: TitleService,
        private errorHandlingService: ErrorHandlingService,
        private readonly stateManagementService: StateManagementService,
	) {
        this.titleService.setPageTitle("Data validation");
    }

    ngOnInit(): void {
        this.pageData$ = combineLatest({
            dataSetInfo: this.dataSetInfoService.getDataSetInfoOriginal$(),
            locked: this.stateManagementService.currentStepLocked$,
            status: this.statusService.status$,
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

	private handleConfirm() {
		this.loadingService.setLoadingStatus(false);
		this.router.navigateByUrl("/anonymizationConfiguration");
	}

    protected readonly Steps = Steps;
}
