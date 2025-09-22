import { HttpClient } from "@angular/common/http";
import { AfterViewInit, Component, OnInit, QueryList, TemplateRef, ViewChild, ViewChildren } from '@angular/core';
import { MatCheckbox } from "@angular/material/checkbox";
import { MatDialog } from "@angular/material/dialog";
import { HoldOutSelector } from "@core/enums/hold-out-selector";
import { ProcessStatus } from "@core/enums/process-status";
import { DataSetInfoService } from "@features/data-upload/services/data-set-info.service";
import { FileService } from "@features/data-upload/services/file.service";
import { EvaluationService } from "@features/evaluation/services/evaluation.service";
import { ExecutionService } from "@features/execution/services/execution.service";
import { DataSetInfo } from "@shared/model/data-set-info";
import { ExecutionStep } from "@shared/model/execution-step";
import { FileType } from "@shared/model/file-configuration";
import { FileInformation } from "@shared/model/file-information";
import { ConfigurationService } from "@shared/services/configuration.service";
import { StatusService } from "@shared/services/status.service";
import { UserService } from "@shared/services/user.service";
import { Observable } from "rxjs";
import { environments } from "src/environments/environment";

@Component({
  selector: 'app-project-export',
  standalone: false,
  templateUrl: './project-export.component.html',
  styleUrl: './project-export.component.less'
})
export class ProjectExportComponent implements OnInit, AfterViewInit {

    protected readonly FileType = FileType;
    protected readonly HoldOutSelector = HoldOutSelector;
    protected readonly ProcessStatus = ProcessStatus;

    protected bundleConfigurations: boolean = true;
    protected datasetFileType: FileType = FileType.CSV;
    protected holdOutSelector: HoldOutSelector = HoldOutSelector.ALL;

    protected numberChecked = 0;

    protected dataSetInfo$: Observable<DataSetInfo>;
    protected evaluationInfo$: Observable<ExecutionStep | null>;
    protected executionInfo$: Observable<ExecutionStep | null>;

    @ViewChild('projectExportDialog') dialogWrap: TemplateRef<any>;
    @ViewChildren('resource') resources: QueryList<MatCheckbox>;

    public constructor(
        protected readonly configurationService: ConfigurationService,
        private readonly dataSetInfoService: DataSetInfoService,
        private readonly dialog: MatDialog,
        protected readonly evaluationService: EvaluationService,
        protected readonly executionService: ExecutionService,
        private readonly fileService: FileService,
        private readonly  http: HttpClient,
        protected readonly statusService: StatusService,
        private readonly userService: UserService,
    ) {
    }

    ngOnInit() {
        this.dataSetInfo$ = this.dataSetInfoService.getDataSetInfo("VALIDATION");
        this.fileService.fileInfo$.subscribe({
            next: (fileInformation: FileInformation) => {
                // Currently, FHIR export is not supported
                if (fileInformation.type != null && fileInformation.type !== FileType.FHIR) {
                    this.datasetFileType = fileInformation.type;
                }
            }
        });

        this.evaluationInfo$ = this.evaluationService.status$;
        this.executionInfo$ = this.executionService.status$;
    }

    public ngAfterViewInit(): void {
        this.updateNumberChecked();
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
     * Toggles all resources.
     * @param select The new value.
     * @protected
     */
    protected toggleAll(select: boolean): void {
        this.resources.filter(resource => !resource.disabled).forEach(resource => resource.checked = select);
        this.updateNumberChecked();
    }

    /**
     * Updates the number of checked checkboxes.
     * @protected
     */
    protected updateNumberChecked(): void {
        this.numberChecked = this.resources.filter(value => value.checked).length;
    }

    /**
     * Downloads all files related to the project in a ZIP file.
     * @protected
     */
    protected exportProject(): void {
        const results = [];
        for (const resultSelector of this.resources.toArray()) {
            if (resultSelector.checked) {
                results.push(resultSelector.value);
            }
        }

        this.http.get(environments.apiUrl + "/api/project/zip", {
            observe: 'response',
            params: {
                bundleConfigurations: this.bundleConfigurations,
                datasetFileType: this.datasetFileType,
                holdOutSelector: this.holdOutSelector,
                resources: results,
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
