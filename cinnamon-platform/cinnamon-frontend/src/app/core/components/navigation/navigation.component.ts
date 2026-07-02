import { KeyValue } from '@angular/common';
import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { MatDialog } from "@angular/material/dialog";
import { NavigationService } from "@core/services/navigation.service";
import { StateManagementService } from "@core/services/state-management.service";
import { ProjectExportComponent } from "@shared/components/project-export/project-export.component";
import { NavigationKey } from "@shared/model/navigation";
import { StatusService } from "@shared/services/status.service";
import { combineLatest, Observable } from "rxjs";
import { ProjectSettingsComponent } from "src/app/shared/components/project-settings/project-settings.component";
import { UserService } from 'src/app/shared/services/user.service';
import { Mode } from '../../enums/mode';
import { StepConfiguration, Steps } from '../../enums/steps';

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

    @ViewChild(ProjectExportComponent) private projectExport: ProjectExportComponent;
    @ViewChild(ProjectSettingsComponent) private projectSettings: ProjectSettingsComponent;

    protected pageData$: Observable<{
        navigationKey: NavigationKey,
    }>;

    constructor(
        private readonly dialog: MatDialog,
        private readonly navigationService: NavigationService,
        protected readonly stateManagementService: StateManagementService,
        protected statusService: StatusService,
        public userService: UserService,
    ) { }

    public ngOnInit(): void {
        this.pageData$ = combineLatest({
            navigationKey: this.navigationService.navigationKey$,
        });
    }

    indexOrderAsc = (akv: KeyValue<string, any>, bkv: KeyValue<string, any>): number => {
        const a = akv.value.index;
        const b = bkv.value.index;

        return a > b ? 1 : (b > a ? -1 : 0);
    };

    onLogout() {
        this.userService.logout("close");
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
