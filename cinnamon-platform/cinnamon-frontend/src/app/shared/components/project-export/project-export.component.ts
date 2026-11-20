import { AfterViewInit, Component, OnInit, QueryList, TemplateRef, ViewChild, ViewChildren, ChangeDetectionStrategy } from '@angular/core';
import { MatCheckbox } from "@angular/material/checkbox";
import { MatDialog } from "@angular/material/dialog";
import { HoldOutSelector } from "@core/enums/hold-out-selector";
import { ProcessStatus } from "@core/enums/process-status";
import { Steps } from "@core/enums/steps";
import { DataSetInfoService } from "@features/data-upload/services/data-set-info.service";
import { FileService } from "@features/data-upload/services/file.service";
import { EvaluationService } from "@features/evaluation/services/evaluation.service";
import { ExecutionService } from "@features/execution/services/execution.service";
import { DataSetInfo } from "@shared/model/data-set-info";
import { ExecutionStep } from "@shared/model/execution-step";
import { FileType } from "@shared/model/file-configuration";
import { FileInformation } from "@shared/model/file-information";
import { ConfigurationService } from "@shared/services/configuration.service";
import { ExportService } from "@shared/services/export.service";
import { StatusService } from "@shared/services/status.service";
import { distinctUntilChanged, Observable } from "rxjs";

@Component({
  selector: 'app-project-export',
  standalone: false,
  templateUrl: './project-export.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './project-export.component.less'
})
export class ProjectExportComponent implements OnInit, AfterViewInit {

    protected readonly Steps = Steps;
    protected readonly FileType = FileType;
    protected readonly HoldOutSelector = HoldOutSelector;
    protected readonly ProcessStatus = ProcessStatus;

    protected bundleConfigurations: boolean = true;
    protected datasetFileType: FileType = FileType.CSV;
    protected holdOutSelector: HoldOutSelector = HoldOutSelector.ALL;

    protected selectedResources: string[] = [];
    protected numberChecked = 0;

    protected dataSetInfo$: Observable<DataSetInfo>;
    protected evaluationInfo$: Observable<ExecutionStep | null>;
    protected executionInfo$: Observable<ExecutionStep | null>;
    protected exportStatus$: Observable<boolean>;

    @ViewChild('projectExportDialog') dialogWrap: TemplateRef<any>;
    @ViewChildren('resource') resources: QueryList<MatCheckbox>;

    public constructor(
        protected readonly configurationService: ConfigurationService,
        private readonly dataSetInfoService: DataSetInfoService,
        private readonly dialog: MatDialog,
        protected readonly evaluationService: EvaluationService,
        protected readonly executionService: ExecutionService,
        private readonly exportService: ExportService,
        private readonly fileService: FileService,
        protected readonly statusService: StatusService,
    ) {
    }

    ngOnInit() {
        this.dataSetInfo$ = this.dataSetInfoService.getDataSetInfo("VALIDATION");
        this.fileService.fileInfo$.subscribe({
            next: (fileInformation: FileInformation) => {
                if (fileInformation.type != null) {
                    this.datasetFileType = fileInformation.type;
                }
            }
        });

        this.evaluationInfo$ = this.evaluationService.status$.pipe(
            distinctUntilChanged((previous, current) => this.areStagesEqual(previous, current)),
        );
        this.executionInfo$ = this.executionService.status$.pipe(
            distinctUntilChanged((previous, current) => this.areStagesEqual(previous, current)),
        );
        this.exportStatus$ = this.exportService.exportStatus$();
    }

    public ngAfterViewInit(): void {
        this.updateChecked();
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
        this.updateChecked();
    }

    /**
     * Updates the selected resources.
     * @protected
     */
    protected updateChecked(): void {
        this.selectedResources = this.getResources();
        this.numberChecked = this.selectedResources.length;
    }

    /**
     * Downloads all files related to the project in a ZIP file.
     * @protected
     */
    protected exportProject(): void {
        this.exportService.exportProject(this.bundleConfigurations, this.datasetFileType, this.holdOutSelector, this.getResources());
    }

    /**
     * Collects the selected resources for export.
     * @returns The selected resources.
     */
    private getResources(): string[] {
        return this.resources
            .filter(checkbox => !checkbox.disabled && checkbox.checked)
            .map(checkbox => checkbox.value);
    }

    /**
     * Checks if the statuses of both stages are equal.
     * @param stageA Stage to be compared.
     * @param stageB Stage to be compared.
     * @returns True if both stages have the same status, false otherwise.
     */
    private areStagesEqual(stageA: ExecutionStep | null, stageB: ExecutionStep | null): boolean {
        if (stageA == null && stageB == null) {
            return true;
        }

        if (stageA == null || stageB == null) {
            return false;
        }

        let isEqual = true;
        for (let job = 0; job < stageA.processes.length; job++) {
            if (stageA.processes[job].externalProcessStatus !== stageB.processes[job].externalProcessStatus) {
                isEqual = false;
                break;
            }
        }
        return isEqual;
    }
}
