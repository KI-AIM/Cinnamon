import { HttpClient } from "@angular/common/http";
import { Component, OnDestroy, OnInit, QueryList, TemplateRef, ViewChild, ViewChildren } from '@angular/core';
import { MatCheckbox } from "@angular/material/checkbox";
import { MatDialog } from "@angular/material/dialog";
import { HoldOutSelector } from "@core/enums/hold-out-selector";
import { DataSetInfoService } from "@features/data-upload/services/data-set-info.service";
import { DataSetInfo } from "@shared/model/data-set-info";
import { ConfigurationService } from "@shared/services/configuration.service";
import { StatusService } from "@shared/services/status.service";
import { UserService } from "@shared/services/user.service";
import { Observable, Subject } from "rxjs";
import { environments } from "src/environments/environment";

@Component({
  selector: 'app-project-export',
  standalone: false,
  templateUrl: './project-export.component.html',
  styleUrl: './project-export.component.less'
})
export class ProjectExportComponent implements OnInit, OnDestroy {

    protected readonly HoldOutSelector = HoldOutSelector;

    protected bundleConfigurations: boolean = true;
    protected holdOutSelector: HoldOutSelector = HoldOutSelector.ALL;

    protected numberChecked = 14;

    protected clickSubject = new Subject<void>();
    protected dataSetInfo$: Observable<DataSetInfo>;

    @ViewChild('projectExportDialog') dialogWrap: TemplateRef<any>;
    @ViewChildren('result') resultSelectors: QueryList<MatCheckbox>;

    public constructor(
        protected readonly configurationService: ConfigurationService,
        private readonly dataSetInfoService: DataSetInfoService,
        private readonly dialog: MatDialog,
        private readonly  http: HttpClient,
        protected readonly statusService: StatusService,
        private readonly userService: UserService,
    ) {
    }

    ngOnInit() {
        this.dataSetInfo$ = this.dataSetInfoService.getDataSetInfo("VALIDATION");
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
            observe: 'response',
            params: {
                bundleConfigurations: this.bundleConfigurations,
                configurationNames: configNames,
                holdOutSelector: this.holdOutSelector,
                results: results,
            },
            responseType: 'arraybuffer'
        }).subscribe({
            next: response => {
                const contentType = response.headers.get("Content-Type");
                let fileName = response.headers.get("Content-Disposition");

                const blob = new Blob([response.body!], {
                    type: contentType!,
                });

                if (fileName != null) {
                    fileName = fileName.split("\"")[1];
                } else {
                    fileName = this.userService.getUser().email + "_Cinnamon-export_" + new Date().toISOString().slice(0, 10) + ".zip";
                }

                const element = document.createElement('a');
                element.href = URL.createObjectURL(blob);
                element.download = fileName;
                document.body.appendChild(element);
                element.click();
            }
        });
    }
}
