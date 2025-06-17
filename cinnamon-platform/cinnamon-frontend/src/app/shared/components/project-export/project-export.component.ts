import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, OnDestroy, TemplateRef, ViewChild } from '@angular/core';
import { MatDialog } from "@angular/material/dialog";
import { ConfigurationService } from "@shared/services/configuration.service";
import { StatusService } from "@shared/services/status.service";
import { debounceTime, filter, from, scan, Subject, switchMap } from "rxjs";
import { environments } from "src/environments/environment";

@Component({
  selector: 'app-project-export',
  standalone: false,
  templateUrl: './project-export.component.html',
  styleUrl: './project-export.component.less'
})
export class ProjectExportComponent implements OnDestroy {

    protected bundleConfigurations: boolean = false;

    protected numberChecked = 0;

    protected clickSubject = new Subject<void>();

    @ViewChild('projectExportDialog') dialogWrap: TemplateRef<any>;

    public constructor(
        protected readonly configurationService: ConfigurationService,
        private readonly dialog: MatDialog,
        private readonly  http: HttpClient,
        protected readonly statusService: StatusService,
    ) {
    }

    public ngOnDestroy(): void {
        this.clickSubject.complete();
    }

    /**
     * Opens the dialog.
     */
    public open(): void {
        this.dialog.open(this.dialogWrap, {
            width: '60%'
        });
    }

    /**
     * Updates the number of checked checkboxes.
     * @param event The input event.
     * @protected
     */
    protected updateNumberChecked(event: Event): void {
        if ((event.target as HTMLInputElement).checked) {
            this.numberChecked++;
        } else {
            this.numberChecked--;
        }
    }

    /**
     * Downloads all files related to the project in a ZIP file.
     * @protected
     */
    protected exportProject(): void {
        const configNames = this.configurationService.getRegisteredConfigurations().filter(value => {
            return (document.getElementById(value.name + "-input") as HTMLInputElement).checked;
        }).map(value => value.name);

        this.http.get(environments.apiUrl + "/api/project/zip", {
            params: {
                bundleConfigurations: this.bundleConfigurations,
                configurationNames: configNames,
            },
            responseType: 'arraybuffer'
        }).subscribe({
            next: data => {
                const blob = new Blob([data], {
                    type: 'application/zip'
                });
                const url = window.URL.createObjectURL(blob);
                window.open(url);
            }
        });
    }
}
