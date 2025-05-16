import { Component, OnDestroy, TemplateRef, ViewChild } from '@angular/core';
import { MatDialog } from "@angular/material/dialog";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { debounceTime, filter, from, map, reduce, scan, Subject, switchMap, toArray } from "rxjs";
import { Steps } from "../../../core/enums/steps";
import { ConfigurationService } from 'src/app/shared/services/configuration.service';
import { StatusService } from "../../services/status.service";

@Component({
    selector: 'app-configuration-management',
    templateUrl: './configuration-management.component.html',
    styleUrls: ['./configuration-management.component.less'],
    standalone: false
})
export class ConfigurationManagementComponent implements OnDestroy {
    protected readonly Steps = Steps;

    @ViewChild('configurationManagement') dialogWrap: TemplateRef<any>;

    protected clickSubject = new Subject<void>();
    private destroy$ = new Subject<void>();

    constructor(
        public configurationService: ConfigurationService,
        public dialog: MatDialog,
        private errorHandlingService: ErrorHandlingService,
        protected readonly statusService: StatusService,
    ) {
        this.clickSubject.pipe(
            switchMap(_ => {
                return from(this.configurationService.getRegisteredConfigurations());
            }),
            filter(value => {
                return (document.getElementById(value.name + "-input") as HTMLInputElement).checked;
            }),
            scan((acc, value) => acc.concat(value.name), [] as string[]),
            debounceTime(0),
            switchMap(value => {
                return this.configurationService.downloadAllConfigurations(value)
            }),
        ).subscribe({
           error: error => {
               this.errorHandlingService.addError(error);
           }
        });
    }

    public ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
        this.clickSubject.complete();
    }

    /**
     * Opens the dialog.
     */
    openDialog() {
        this.dialog.open(this.dialogWrap, {
            width: '60%'
        });
    }
}
