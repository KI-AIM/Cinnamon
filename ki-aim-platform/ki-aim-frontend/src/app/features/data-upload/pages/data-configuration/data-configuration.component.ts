import { Component, OnInit, QueryList, ViewChild, ViewChildren } from '@angular/core';
import { TitleService } from 'src/app/core/services/title-service.service';
import { DataConfigurationService } from 'src/app/shared/services/data-configuration.service';
import { DataService } from 'src/app/shared/services/data.service';
import { FileService } from '../../services/file.service';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Steps } from 'src/app/core/enums/steps';
import { plainToInstance } from 'class-transformer';
import { LoadingService } from 'src/app/shared/services/loading.service';
import {
    AttributeConfigurationComponent
} from "../../components/attribute-configuration/attribute-configuration.component";
import {
    ConfigurationUploadComponent
} from "../../../../shared/components/configuration-upload/configuration-upload.component";
import { ImportPipeData } from "../../../../shared/model/import-pipe-data";
import { ErrorResponse } from 'src/app/shared/model/error-response';
import { ErrorMessageService } from 'src/app/shared/services/error-message.service';
import { FileType } from 'src/app/shared/model/file-configuration';
import { StatusService } from "../../../../shared/services/status.service";
import { DataConfiguration } from 'src/app/shared/model/data-configuration';
import { Subscription } from "rxjs";
import { FormArray, FormBuilder, FormGroup, Validators } from "@angular/forms";
import { noSpaceValidator } from "../../../../shared/directives/no-space-validator.directive";
import { stringify } from "yaml";

@Component({
    selector: 'app-data-configuration',
    templateUrl: './data-configuration.component.html',
    styleUrls: ['./data-configuration.component.less'],
})
export class DataConfigurationComponent implements OnInit {
    error: string;
    FileType = FileType;
    dataConfiguration: DataConfiguration
    private dataConfigurationSubscription: Subscription;

    protected form: FormGroup;

    @ViewChild('configurationUpload') configurationUpload: ConfigurationUploadComponent;
    @ViewChildren('attributeConfiguration') attributeConfigurations: QueryList<AttributeConfigurationComponent>;

    constructor(
        public configuration: DataConfigurationService,
        public dataService: DataService,
        public fileService: FileService,
        private readonly formBuilder: FormBuilder,
        private titleService: TitleService,
        private router: Router,
        private readonly statusService: StatusService,
        public loadingService: LoadingService,
		private errorMessageService: ErrorMessageService,
    ) {
        this.error = "";
        this.titleService.setPageTitle("Data configuration");
    }

    protected get locked(): boolean {
        return this.statusService.isStepCompleted(Steps.DATA_CONFIG)
    }

    ngOnInit(): void {
        this.dataConfigurationSubscription = this.configuration.dataConfiguration$.subscribe(value => {
            this.setEmptyColumnNames(value);
            this.form = this.createForm(value);
            // this.dataConfiguration = value;
        })
    }

    ngOnDestroy() {
        this.dataConfigurationSubscription.unsubscribe();
    }

    confirmConfiguration() {
        console.log(this.form.getRawValue());
        console.log(this.form.value);
        console.log(stringify(this.form.getRawValue()));
        console.log(stringify(this.form.value));
        this.loadingService.setLoadingStatus(true);

        this.dataService.storeData(this.fileService.getFile(),
            this.dataConfiguration,
            this.fileService.getFileConfiguration()
        ).subscribe({
            next: (d) => this.handleUpload(d),
            error: (e) => this.handleError(e),
        });
    }

    private setEmptyColumnNames(dataConfiguration: DataConfiguration) {
        dataConfiguration.configurations.forEach((column, index) => {
            if (column.name === undefined || column.name === null || column.name == "") {
                column.name = 'column_' + index;
            }
        });
    }

    private handleUpload(data: Object) {
        this.loadingService.setLoadingStatus(false);

        this.router.navigateByUrl("/dataValidation");
        this. statusService.setNextStep(Steps.VALIDATION);
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

        const configurationsFrom = (this.form.controls['configurations'] as FormArray).controls
        // Find duplicate column names
        for (const f of Object.values(configurationsFrom)) {
            const fg = (f as FormGroup).controls['name'];

            const name = fg.value;
            if (name !== "") {
                if (names.includes(name)) {
                    duplicates.push(name);
                } else {
                    names.push(name);
                }
            }
        }

        // Add errors to inputs with duplicate column namesa
        for (const f of Object.values(configurationsFrom)) {
            const fg = (f as FormGroup).controls['name'];

            const name = fg.value;
            if (duplicates.includes(name)) {
                fg.setErrors({unique: true});
            } else {
                fg.setErrors(null);
                fg.updateValueAndValidity();
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

    protected getColumnConfigurationForms(form: FormGroup): FormGroup[] {
        return (form.controls['configurations'] as FormArray).controls as FormGroup[];
    }

    private createForm(dataConfiguration: DataConfiguration): FormGroup {
        const formArray: any[] = [];
        console.log(dataConfiguration);
        dataConfiguration.configurations.forEach(columnConfiguration=> {
            const addConfigs = [];

            for (const addConfig of columnConfiguration.configurations) {
                console.log(addConfig.getName());
                if (addConfig.getName() === "DateFormatConfiguration") {
                 addConfigs.push(
                     this.formBuilder.group({
                         name: ["DateFormatConfiguration"],
                         dateFormatter: ["", {validators: [Validators.required]}],
                     })
                 );
                } else if (addConfig.getName() === "DateTimeFormatConfiguration") {
                    addConfigs.push(
                        this.formBuilder.group({
                            name: ["DateTimeFormatConfiguration"],
                            dateTimeFormatter: ["", {validators: [Validators.required]}],
                        })
                    );
                } else if (addConfig.getName() === "RangeConfiguration") {
                    addConfigs.push(
                        this.formBuilder.group({
                            name: ["RangeConfiguration"],
                            minValue: ["", {validators: [Validators.required]}],
                            maxValue: ["", {validators: [Validators.required]}],
                        })
                    );
                } else if (addConfig.getName() === "StringPatternConfiguration") {
                    addConfigs.push(
                        this.formBuilder.group({
                            name: ["StringPatternConfiguration"],
                            pattern: ["", {validators: [Validators.required]}],
                        })
                    );
                }
            }
            console.log(addConfigs);

            const columnGroup = this.formBuilder.group({
                index: [columnConfiguration.index],
                name: [columnConfiguration.name, {disabled: this.locked, validators: [Validators.required, noSpaceValidator()]}],
                type: [columnConfiguration.type],
                scale: [columnConfiguration.scale],
                configurations: this.formBuilder.array(addConfigs),
            });

            formArray.push(columnGroup);
        });

        return this.formBuilder.group({configurations: this.formBuilder.array(formArray)});
    }
}
