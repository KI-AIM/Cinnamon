import { Component, OnInit, QueryList, ViewChild, ViewChildren } from '@angular/core';
import { TitleService } from 'src/app/core/services/title-service.service';
import { DataConfigurationService } from 'src/app/shared/services/data-configuration.service';
import { DataService } from 'src/app/shared/services/data.service';
import { FileService } from '../../services/file.service';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { StateManagementService } from 'src/app/core/services/state-management.service';
import { Steps } from 'src/app/core/enums/steps';
import { plainToClass, plainToInstance } from 'class-transformer';
import { TransformationService } from '../../services/transformation.service';
import { TransformationResult } from 'src/app/shared/model/transformation-result';
import { LoadingService } from 'src/app/shared/services/loading.service';
import {
    AttributeConfigurationComponent
} from "../../components/attribute-configuration/attribute-configuration.component";
import {
    ConfigurationUploadComponent
} from "../../../configuration/components/configuration-upload/configuration-upload.component";
import { ImportPipeData } from "../../../../shared/model/import-pipe-data";
import { ErrorResponse } from 'src/app/shared/model/error-response';
import { ErrorMessageService } from 'src/app/shared/services/error-message.service';
import { FileType } from 'src/app/shared/model/file-configuration';

@Component({
    selector: 'app-data-configuration',
    templateUrl: './data-configuration.component.html',
    styleUrls: ['./data-configuration.component.less'],
})
export class DataConfigurationComponent implements OnInit {
    error: string;
    FileType = FileType;
    isValid: boolean;

    @ViewChild('configurationUpload') configurationUpload: ConfigurationUploadComponent;
    @ViewChildren('attributeConfiguration') attributeConfigurations: QueryList<AttributeConfigurationComponent>;

    constructor(
        public configuration: DataConfigurationService,
        public dataService: DataService,
        public fileService: FileService,
        private titleService: TitleService,
        private router: Router,
        private stateManagement: StateManagementService,
        private transformationService: TransformationService,
        public loadingService: LoadingService,
		private errorMessageService: ErrorMessageService,
    ) {
        this.error = "";
        this.isValid = true;
        this.titleService.setPageTitle("Data configuration");
    }

    ngOnInit(): void {
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

    onValidation(isValid: boolean) {
        this.isValid = isValid;
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
        this.error = this.errorMessageService.extractErrorMessage(error) + "a";

        window.scroll(0, 0);
    }

    /**
     * Validates if all column names are unique.
     */
    protected checkUniqueColumnNames() {
        const names: string[] = [];
        const duplicates: string[] = [];

        // Find duplicate column names
        for (const hun of this.attributeConfigurations) {

            if (hun.nameInput.value !== "") {
                if (names.includes(hun.nameInput.value)) {
                    duplicates.push(hun.nameInput.value);
                } else {
                    names.push(hun.nameInput.value);
                }
            }
        }

        // Add errors to inputs with duplicate column names
        for (const hun of this.attributeConfigurations) {
            if (duplicates.includes(hun.nameInput.value)) {
                hun.nameInput.control.setErrors({unique: true});
            } else {
                // Delete all errors and revalidate to add existing errors
                hun.nameInput.control.setErrors(null);
                hun.nameInput.control.updateValueAndValidity();
            }
        }
    }

    protected handleConfigUpload(result: ImportPipeData[] | null) {
        if (result === null) {
            this.error = "Something went wrong! Please try again later.";
            return;
        }

        const configImportData = result[0]
        if (configImportData.hasOwnProperty('error') && configImportData['error'] instanceof HttpErrorResponse) {
            let errorMessage = "";
            const errorResponse = plainToInstance(ErrorResponse, configImportData.error.error);

            if (errorResponse.errorCode === '3-2-1') {
                for (const [field, errors] of Object.entries(errorResponse.errorDetails)) {
                    const parts = field.split(".");
                    if (parts.length === 3) {
                    } else {
                        errorMessage += (errors as string[]).join(", ") + "\n";
                    }
                }

            } else {
                errorMessage = errorResponse.errorMessage;
            }
            this.error = errorMessage;
        }

        this.configurationUpload.closeDialog();
    }
}
