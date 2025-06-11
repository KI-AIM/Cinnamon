import { Component, OnDestroy, TemplateRef, ViewChild } from '@angular/core';
import { MatDialog } from "@angular/material/dialog";
import { Steps } from "@core/enums/steps";
import { ConfigurationService } from '@shared/services/configuration.service';
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { StatusService } from "@shared/services/status.service";
import { debounceTime, filter, from, Observable, scan, Subject, switchMap, takeUntil } from "rxjs";

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
            takeUntil(this.destroy$),
            switchMap(_ => {
                return this.downloadAllConfigurations();
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

    /**
     * Downloads all configurations that corresponding checkboxes are checked.
     * @private
     */
    private downloadAllConfigurations(): Observable<string> {
        return from(this.configurationService.getRegisteredConfigurations()).pipe(
            filter(value => {
                return (document.getElementById(value.name + "-input") as HTMLInputElement).checked;
            }),
            scan((acc, value) => acc.concat(value.name), [] as string[]),
            debounceTime(0),
            switchMap(value => {
                return this.configurationService.downloadAllConfigurations(value)
            }),
        );
    }
}
