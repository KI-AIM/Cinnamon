import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, OnDestroy, QueryList, TemplateRef, ViewChild, ViewChildren } from '@angular/core';
import { MatCheckbox } from "@angular/material/checkbox";
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

    protected bundleConfigurations: boolean = true;

    protected numberChecked = 14;

    protected clickSubject = new Subject<void>();

    @ViewChild('projectExportDialog') dialogWrap: TemplateRef<any>;
    @ViewChildren('result') resultSelectors: QueryList<MatCheckbox>;

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

        const results = [];
        for (const resultSelector of this.resultSelectors.toArray()) {
            if (resultSelector.checked) {
                results.push(resultSelector.value);
            }
        }

        this.http.get(environments.apiUrl + "/api/project/zip", {
            params: {
                bundleConfigurations: this.bundleConfigurations,
                configurationNames: configNames,
                results: results,
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
