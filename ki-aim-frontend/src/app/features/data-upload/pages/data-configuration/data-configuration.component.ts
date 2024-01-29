import { Component } from '@angular/core';
import { TitleService } from 'src/app/core/services/title-service.service';
import { DataConfigurationService } from 'src/app/shared/services/data-configuration.service';
import { DataService } from 'src/app/shared/services/data.service';
import { FileService } from '../../services/file.service';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { StateManagementService } from 'src/app/core/services/state-management.service';
import { Steps } from 'src/app/core/enums/steps';
import { plainToClass } from 'class-transformer';
import { TransformationService } from '../../services/transformation.service';
import { TransformationResult } from 'src/app/shared/model/transformation-result';
import { LoadingService } from 'src/app/shared/services/loading.service';

@Component({
    selector: 'app-data-configuration',
    templateUrl: './data-configuration.component.html',
    styleUrls: ['./data-configuration.component.less'],
})
export class DataConfigurationComponent {

    constructor(
        public configuration: DataConfigurationService,
        public dataService: DataService,
        private titleService: TitleService,
        private fileService: FileService,
        private router: Router,
        private stateManagement: StateManagementService,
        private transformationService: TransformationService,
        public loadingService: LoadingService,
    ) {
        this.titleService.setPageTitle("Data configuration");
    }

	ngAfterViewInit() {
        this.setEmptyColumnNames();
    }

    confirmConfiguration() {
        this.loadingService.setLoadingStatus(true);

        this.dataService.readAndValidateData(this.fileService.getFile(),
            this.fileService.getFileConfiguration(),
            this.configuration.getDataConfiguration()
        ).subscribe({
            next: (d) => this.handleUpload(d),
            error: (e) => this.handleError(e),
        });
    }

    downloadConfiguration() {
        this.configuration.downloadDataConfigurationAsYaml().subscribe({
            next: (data: Blob) => {
                const blob = new Blob([data], { type: 'text/yaml' });
                const fileName = this.fileService.getFile().name
                this.saveFile(blob, fileName);
            },
            error: (error) => {

            },
        });
    }

    private setEmptyColumnNames() {
        this.configuration.getDataConfiguration().configurations.forEach((column, index) => {
            if (column.name == undefined || column.name == null || column.name == "") {
                column.name = 'column_' + index;
            }
        });
    }

    private handleUpload(data: Object) {
        this.transformationService.setTransformationResult(plainToClass(TransformationResult, data));
        this.loadingService.setLoadingStatus(false);

        this.router.navigateByUrl("/dataValidation");
        this.stateManagement.addCompletedStep(Steps.DATA_CONFIG);
    }

    private handleError(error: HttpErrorResponse) {
        this.loadingService.setLoadingStatus(false); 

        //TODO implement me
    }

    private saveFile(fileData: Blob, fileName: string) {
        const anchor = document.createElement('a');
        anchor.href = URL.createObjectURL(fileData);
        anchor.download = fileName;
        document.body.appendChild(anchor);
        anchor.click();
        document.body.removeChild(anchor);
    }
}
