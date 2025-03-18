import { Component, OnDestroy, OnInit, QueryList, ViewChild, ViewChildren } from '@angular/core';
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
import { debounceTime, distinctUntilChanged, map, Observable, of, Subscription, switchMap } from "rxjs";
import { FormArray, FormBuilder, FormGroup, Validators } from "@angular/forms";
import { noSpaceValidator } from "../../../../shared/directives/no-space-validator.directive";
import { DateFormatConfiguration } from "../../../../shared/model/date-format-configuration";
import { DateTimeFormatConfiguration } from "../../../../shared/model/date-time-format-configuration";
import { RangeConfiguration } from "../../../../shared/model/range-configuration";
import { StringPatternConfiguration } from "../../../../shared/model/string-pattern-configuration";
import { DataSetInfoService } from "../../services/data-set-info.service";

@Component({
    selector: 'app-data-configuration',
    templateUrl: './data-configuration.component.html',
    styleUrls: ['./data-configuration.component.less'],
    standalone: false
})
export class DataConfigurationComponent implements OnInit, OnDestroy {
    error: string;

    private dataConfigurationSubscription: Subscription;

    protected form: FormGroup;
    protected isAdvanceConfigurationExpanded: boolean = false;
    protected createSplit: boolean = false;
    protected holdOutSplitPercentage: number = 0.2;

    protected isFileTypeXLSX$: Observable<boolean>;

    @ViewChild('configurationUpload') configurationUpload: ConfigurationUploadComponent;
    @ViewChildren('attributeConfiguration') attributeConfigurations: QueryList<AttributeConfigurationComponent>;

    constructor(
        public configuration: DataConfigurationService,
        public dataService: DataService,
        private dataSetInfoService: DataSetInfoService,
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
        return this.statusService.isStepCompleted(Steps.VALIDATION)
    }

    ngOnInit(): void {
        this.isFileTypeXLSX$ = this.fileService.fileInfo$.pipe(
            map(value => {
               return value.type === FileType.XLSX;
            })
        );

        this.dataConfigurationSubscription = this.configuration.dataConfiguration$.subscribe(value => {
            if (this.configuration.localDataConfiguration !== null) {
                this.form = this.createForm(this.configuration.localDataConfiguration);
            } else {
                this.setEmptyColumnNames(value);
                this.form = this.createForm(value);
            }

            this.form.valueChanges.pipe(debounceTime(300), distinctUntilChanged()).subscribe(value1 => {
                this.configuration.localDataConfiguration = plainToInstance(DataConfiguration, value1);
            });
        });

        this.dataSetInfoService.getDataSetInfoOriginal$().subscribe({
            next: value => {
                this.createSplit = value.hasHoldOutSplit;
                if (value.hasHoldOutSplit) {
                    this.holdOutSplitPercentage = value.holdOutPercentage;
                }
            }
        });
    }

    ngOnDestroy() {
        this.dataConfigurationSubscription.unsubscribe();
    }

    confirmConfiguration() {
        const config = plainToInstance(DataConfiguration, this.form.value);
        this.loadingService.setLoadingStatus(true);
        this.configuration.setDataConfiguration(config);
        this.dataService.storeData(config).pipe(
            switchMap(id => {
                if (this.createSplit) {
                    return this.dataService.createHoldOutSplit(this.holdOutSplitPercentage);
                } else {
                    return of(id);
                }
            }),
            switchMap(() => {
                return this.statusService.updateNextStep(Steps.VALIDATION);
            }),
        ).subscribe({
            next: () => this.handleUpload(),
            error: (e) => this.handleError(e),
        });
    }

    protected updateCreateSplit(newValue: boolean) {
        this.createSplit = newValue;
        if (!newValue) {
            this.isAdvanceConfigurationExpanded = false;
        }
    }

    private setEmptyColumnNames(dataConfiguration: DataConfiguration) {
        dataConfiguration.configurations.forEach((column, index) => {
            if (column.name === undefined || column.name === null || column.name == "") {
                column.name = 'column_' + index;
            }
        });
    }

    private handleUpload() {
        this.loadingService.setLoadingStatus(false);
        this.dataSetInfoService.invalidateCache();

        this.router.navigateByUrl("/dataValidation");
    }

    private handleError(error: HttpErrorResponse) {
        this.loadingService.setLoadingStatus(false);
        this.error = this.errorMessageService.extractErrorMessage(error);

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
                if (fg.errors && fg.errors['unique']) {
                    fg.setErrors(null);
                    fg.updateValueAndValidity();
                }
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
        dataConfiguration.configurations.forEach(columnConfiguration=> {
            const addConfigs = [];

            for (const addConfig of columnConfiguration.configurations) {
                if (addConfig.getName() === "DateFormatConfiguration") {
                    const dateFormatConfiguration = addConfig as DateFormatConfiguration;
                    addConfigs.push(
                        this.formBuilder.group({
                            name: ["DateFormatConfiguration"],
                            dateFormatter: [{
                                value: dateFormatConfiguration.dateFormatter,
                                disabled: this.locked
                            }, {validators: [Validators.required]}],
                        })
                    );
                } else if (addConfig.getName() === "DateTimeFormatConfiguration") {
                    const dateTimeFormatConfiguration = addConfig as DateTimeFormatConfiguration;
                    addConfigs.push(
                        this.formBuilder.group({
                            name: ["DateTimeFormatConfiguration"],
                            dateTimeFormatter: [{
                                value: dateTimeFormatConfiguration.dateTimeFormatter,
                                disabled: this.locked
                            }, {
                                validators: [Validators.required]
                            }],
                        })
                    );
                } else if (addConfig.getName() === "RangeConfiguration") {
                    const rangeConfiguration = addConfig as RangeConfiguration;
                    addConfigs.push(
                        this.formBuilder.group({
                            name: ["RangeConfiguration"],
                            minValue: [{value: rangeConfiguration.minValue, disabled: this.locked}, {
                                validators: [Validators.required]
                            }],
                            maxValue: [{value: rangeConfiguration.maxValue, disabled: this.locked}, {
                                validators: [Validators.required]
                            }],
                        })
                    );
                } else if (addConfig.getName() === "StringPatternConfiguration") {
                    const stringPatternConfiguration = addConfig as StringPatternConfiguration;
                    addConfigs.push(
                        this.formBuilder.group({
                            name: ["StringPatternConfiguration"],
                            pattern: [{value: stringPatternConfiguration.pattern, disabled: this.locked}, {
                                validators: [Validators.required]
                            }],
                        })
                    );
                }
            }

            const columnGroup = this.formBuilder.group({
                index: [columnConfiguration.index],
                name: [{value: columnConfiguration.name, disabled: this.locked}, {
                    validators: [Validators.required, noSpaceValidator()]
                }],
                type: [{value: columnConfiguration.type, disabled: this.locked}, {validators: [Validators.required]}],
                scale: [{value: columnConfiguration.scale, disabled: this.locked}, {
                    disabled: this.locked,
                    validators: [Validators.required]
                }],
                configurations: this.formBuilder.array(addConfigs),
            });

            formArray.push(columnGroup);
        });

        return this.formBuilder.group({configurations: this.formBuilder.array(formArray)});
    }
}
