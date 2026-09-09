import { Component, OnInit } from "@angular/core";
import { Mode } from "@core/enums/mode";
import { Steps } from "@core/enums/steps";
import { LockedInformation, StateManagementService } from "@core/services/state-management.service";
import { DataSetInfoService } from "@features/data-upload/services/data-set-info.service";
import { DataSetInfo } from "@shared/model/data-set-info";
import { DataConfiguration } from "@shared/model/data-configuration";
import { Status } from "@shared/model/status";
import { DataConfigurationService } from "@shared/services/data-configuration.service";
import { DataService } from "@shared/services/data.service";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { LoadingService } from "@shared/services/loading.service";
import { StatusService } from "@shared/services/status.service";
import { combineLatest, Observable } from "rxjs";

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
        dataConfiguration: DataConfiguration;
        locked: LockedInformation;
        status: Status;
    }>;

	constructor(
		private loadingService: LoadingService,
        private readonly dataConfigurationService: DataConfigurationService,
        private statusService: StatusService,
        protected dataSetInfoService: DataSetInfoService,
		private dataService: DataService,
        private errorHandlingService: ErrorHandlingService,
        private readonly stateManagementService: StateManagementService,
	) {
    }

    ngOnInit(): void {
        this.pageData$ = combineLatest({
            dataSetInfo: this.dataSetInfoService.getDataSetInfoOriginal$(),
            dataConfiguration: this.dataConfigurationService.dataConfiguration$,
            locked: this.stateManagementService.currentStepLocked$,
            status: this.statusService.statusNonNull$,
        });
    }

	confirmData(dataConfiguration: DataConfiguration) {
		this.loadingService.setLoadingStatus(true);

        this.dataService.confirmData().subscribe({
            next: () => this.handleConfirm(dataConfiguration),
            error: (e) => this.errorHandlingService.addError(e),
        });
	}

	private handleConfirm(dataConfiguration: DataConfiguration) {
        this.loadingService.setLoadingStatus(false);
        this.stateManagementService.setAndRouteToStep(Steps.ANONYMIZATION).subscribe();
	}

    protected readonly Steps = Steps;
}
