import { KeyValue } from '@angular/common';
import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { MatDialog } from "@angular/material/dialog";
import { NavigationService } from "@core/services/navigation.service";
import { StateManagementService } from "@core/services/state-management.service";
import { ProjectExportComponent } from "@shared/components/project-export/project-export.component";
import { NavigationKey } from "@shared/model/navigation";
import { StatusService } from "@shared/services/status.service";
import { DataConfiguration, hasTextColumns } from "@shared/model/data-configuration";
import { DataConfigurationService } from "@shared/services/data-configuration.service";
import { catchError, combineLatest, map, Observable, of, switchMap } from "rxjs";
import { ProjectSettingsComponent } from "src/app/shared/components/project-settings/project-settings.component";
import { UserService } from 'src/app/shared/services/user.service';
import { AdminPageConfiguration } from '../../enums/admin-pages';
import { Mode } from '../../enums/mode';
import { StepConfiguration, StepDefinition, Steps } from '../../enums/steps';

@Component({
    selector: 'app-navigation',
    templateUrl: './navigation.component.html',
    styleUrls: ['./navigation.component.less'],
    standalone: false
})

export class NavigationComponent implements OnInit{
    protected readonly NavigationKey = NavigationKey;

    Mode = Mode;
    Steps = Steps;
    StepConfiguration = StepConfiguration;
    AdminPageConfiguration = AdminPageConfiguration;

    @ViewChild(ProjectExportComponent) private projectExport: ProjectExportComponent;
    @ViewChild(ProjectSettingsComponent) private projectSettings: ProjectSettingsComponent;

    protected pageData$: Observable<{
        navigationKey: NavigationKey,
        openStep: StepDefinition | null,
        showDataExtraction: boolean,
    }>;

    constructor(
        private readonly dialog: MatDialog,
        private readonly dataConfigurationService: DataConfigurationService,
        private readonly navigationService: NavigationService,
        protected readonly stateManagementService: StateManagementService,
        protected statusService: StatusService,
        public userService: UserService,
    ) { }

    public ngOnInit(): void {
        const showDataExtraction$ = this.statusService.statusNonNull$.pipe(
            switchMap(status => {
                if (StepConfiguration[status.currentStep].index < StepConfiguration[Steps.DATA_EXTRACTION].index) {
                    return of(false);
                }

                return this.dataConfigurationService.downloadDataConfigurationAsJson().pipe(
                    map((dataConfiguration: DataConfiguration) => hasTextColumns(dataConfiguration)),
                    catchError(() => of(false)),
                );
            }),
        );

        this.pageData$ = combineLatest({
            navigationKey: this.navigationService.navigationKey$,
            openStep: this.stateManagementService.currentStep$,
        }).pipe(
            switchMap(pageData => {
                if (pageData.navigationKey !== NavigationKey.PROJECT) {
                    return of({...pageData, showDataExtraction: false});
                }

                return showDataExtraction$.pipe(
                    map(showDataExtraction => ({...pageData, showDataExtraction})),
                );
            }),
        );
    }

    indexOrderAsc = (akv: KeyValue<string, any>, bkv: KeyValue<string, any>): number => {
        const a = akv.value.index;
        const b = bkv.value.index;

        return a > b ? 1 : (b > a ? -1 : 0);
    };

    onLogout() {
        this.userService.routeToUser$().subscribe();
    }

    protected routeToStep(step: Steps): void {
        this.stateManagementService.routeToStep(step).subscribe({});
    }

    /**
     * Opens the project settings.
     * @protected
     */
    protected openProjectSettings() {
        this.projectSettings.open();
    }

    /**
     * Opens the project export.
     * @protected
     */
    protected openProjectExport(): void {
        this.projectExport.open();
    }

    protected openDialog(templateRef: TemplateRef<any>) {
        this.dialog.open(templateRef, {
            width: '60%'
        });
    }
}
