import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, QueryList, ViewChildren } from '@angular/core';
import { AbstractControl, FormArray, FormBuilder, FormGroup, ValidationErrors, Validators } from "@angular/forms";
import { Router } from '@angular/router';
import { Mode } from "@core/enums/mode";
import { LockedInformation, StateManagementService } from "@core/services/state-management.service";
import { noSpaceValidator } from "@shared/directives/no-space-validator.directive";
import { ConfigurationInputDefinition } from "@shared/model/configuration-input-definition";
import { ConfigurationInputType } from "@shared/model/configuration-input-type";
import { DataSetInfo } from "@shared/model/data-set-info";
import { DateFormatConfiguration } from "@shared/model/date-format-configuration";
import { DateTimeFormatConfiguration } from "@shared/model/date-time-format-configuration";
import { RangeConfiguration } from "@shared/model/range-configuration";
import { Status } from "@shared/model/status";
import { StringPatternConfiguration } from "@shared/model/string-pattern-configuration";
import { ErrorHandlingService } from "@shared/services/error-handling.service";
import { StatusService } from "@shared/services/status.service";
import { plainToInstance } from 'class-transformer';
import {
    catchError,
    combineLatest,
    debounceTime,
    distinctUntilChanged,
    map,
    Observable,
    of,
    switchMap,
    tap
} from "rxjs";
import { Steps } from 'src/app/core/enums/steps';
import { TitleService } from 'src/app/core/services/title-service.service';
import { DataConfiguration } from 'src/app/shared/model/data-configuration';
import { FileType } from 'src/app/shared/model/file-configuration';
import { DataConfigurationService } from 'src/app/shared/services/data-configuration.service';
import { DataService } from 'src/app/shared/services/data.service';
import { LoadingService } from 'src/app/shared/services/loading.service';
import {
    AttributeConfigurationComponent
} from "../../components/attribute-configuration/attribute-configuration.component";
import { DataSetInfoService } from "../../services/data-set-info.service";
import { FileService } from '../../services/file.service';

@Component({
    selector: 'app-data-configuration',
    templateUrl: './data-configuration.component.html',
    styleUrls: ['./data-configuration.component.less'],
    standalone: false
})
export class DataConfigurationComponent implements OnInit {
    protected attributeConfigurationform: FormGroup;
    protected dataSetConfigurationForm: FormGroup | null;

    protected isAdvanceConfigurationExpanded: boolean = false;

    protected pageData$: Observable<{
        dataConfiguration: DataConfiguration,
        dataSetInfo: DataSetInfo | null,
        isFileTypeXLSX: boolean,
        locked: LockedInformation,
        status: Status,
    }>

    @ViewChildren('attributeConfiguration') attributeConfigurations: QueryList<AttributeConfigurationComponent>;

    constructor(
        public configuration: DataConfigurationService,
        public dataService: DataService,
        private dataSetInfoService: DataSetInfoService,
        private readonly errorHandlingService: ErrorHandlingService,
        public fileService: FileService,
        private readonly formBuilder: FormBuilder,
        private titleService: TitleService,
        private router: Router,
        private readonly statusService: StatusService,
        public loadingService: LoadingService,
        private readonly stateManagementService: StateManagementService,
    ) {
        this.titleService.setPageTitle("Data configuration");
    }

    private get locked(): boolean {
        return this.statusService.isStepCompleted(Steps.VALIDATION)
    }

    ngOnInit(): void {

        this.pageData$ = combineLatest({
            dataConfiguration: this.configuration.dataConfiguration$.pipe(
                tap(value => {
                    if (this.configuration.localDataConfiguration !== null) {
                        this.attributeConfigurationform = this.createAttributeConfigurationForm(this.configuration.localDataConfiguration);
                    } else {
                        this.setEmptyColumnNames(value);
                        this.attributeConfigurationform = this.createAttributeConfigurationForm(value);
                    }

                    this.attributeConfigurationform.valueChanges.pipe(debounceTime(300), distinctUntilChanged()).subscribe(value1 => {
                        this.configuration.localDataConfiguration = plainToInstance(DataConfiguration, value1);
                    });
                }),
            ),
            dataSetInfo: this.dataSetInfoService.getDataSetInfoOriginal$().pipe(
                tap(value => {
                    if (this.configuration.localDataSetConfiguration !== null) {
                        this.createDataSetConfigurationForm(this.configuration.localDataSetConfiguration.createHoldOutSplit, this.configuration.localDataSetConfiguration.holdOutSplitPercentage);
                    } else {
                        this.createDataSetConfigurationForm(value.hasHoldOutSplit, value.holdOutPercentage);
                    }
                }),
                catchError(error => {
                    this.handleError(error);
                    return of(null);
                }),
            ),
            isFileTypeXLSX: this.fileService.fileInfo$.pipe(
                map(value => {
                    return value.type === FileType.XLSX;
                }),
            ),
            locked: this.stateManagementService.currentStepLocked$.pipe(
                tap(value => {
                    this.setFormEnabled(this.dataSetConfigurationForm, value.isLocked);
                    this.setFormEnabled(this.attributeConfigurationform, value.isLocked);
                }),
            ),
            status: this.statusService.status$,
        });
    }

    protected get createHoldOutSplit(): boolean {
        return this.dataSetConfigurationForm?.controls['createHoldOutSplit'].value;
    }

    protected get holdOutSplitPercentage(): number {
        return this.dataSetConfigurationForm?.controls['holdOutSplitPercentage'].value;
    }

    protected get isDataSetConfigurationFormInvalid(): boolean {
        return this.dataSetConfigurationForm ? this.dataSetConfigurationForm.invalid : true;
    }

    protected get isInvalid(): boolean {
        return this.attributeConfigurationform.invalid || this.isDataSetConfigurationFormInvalid;
    }

    /**
     * Returns the confidence of the estimation for the attribute with the given index.
     * @param attributeIndex The index of the attribute.
     * @return The confidence.
     * @protected
     */
    protected getConfidence(attributeIndex: number): number | null {
        return this.configuration.confidence ? this.configuration.confidence[attributeIndex] : null;
    }

    confirmConfiguration() {
        const config = plainToInstance(DataConfiguration, this.attributeConfigurationform.value);
        this.loadingService.setLoadingStatus(true);
        this.configuration.setDataConfiguration(config);
        this.dataService.storeData(config).pipe(
            switchMap(id => {
                if (this.createHoldOutSplit) {
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
        this.errorHandlingService.addError(error);

        window.scroll(0, 0);
    }

    /**
     * Validates if all column names are unique.
     */
    protected checkUniqueColumnNames() {
        const names: string[] = [];
        const duplicates: string[] = [];

        const configurationsFrom = (this.attributeConfigurationform.controls['configurations'] as FormArray).controls
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

    protected getColumnConfigurationForms(form: FormGroup): FormGroup[] {
        return (form.controls['configurations'] as FormArray).controls as FormGroup[];
    }

    private createAttributeConfigurationForm(dataConfiguration: DataConfiguration): FormGroup {
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
                type: [{
                    value: columnConfiguration.type, disabled: this.locked
                }, {
                    validators: [Validators.required, this.dataTypeNotUndefined]
                }],
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

    /**
     * Creates the form for the data set configuration.
     * Initializes the form based on the given parameters.
     * @param createHoldOutSplit If a hold-out split should be created.
     * @param holdOutSplitPercentage The percentage of rows to hold out.
     * @private
     */
    private createDataSetConfigurationForm(createHoldOutSplit: boolean, holdOutSplitPercentage: number): void {
        this.dataSetConfigurationForm = this.formBuilder.group({
            createHoldOutSplit: [{value: createHoldOutSplit, disabled: this.locked}],
            holdOutSplitPercentage: [{
                value: holdOutSplitPercentage !== 0 ? holdOutSplitPercentage : 0.2,
                disabled: !createHoldOutSplit || this.locked
            }, {
                validators: [Validators.required, Validators.min(0), Validators.max(1)],
            }],
        });

        this.dataSetConfigurationForm.controls['createHoldOutSplit'].valueChanges.subscribe(value => {
            if (!value) {
                this.dataSetConfigurationForm!.controls['holdOutSplitPercentage'].disable();
                this.isAdvanceConfigurationExpanded = false;
            } else {
                this.dataSetConfigurationForm!.controls['holdOutSplitPercentage'].enable();
            }
        });

        this.dataSetConfigurationForm.valueChanges.pipe(debounceTime(300), distinctUntilChanged()).subscribe(_ => {
            this.configuration.localDataSetConfiguration = this.dataSetConfigurationForm!.getRawValue();
        });
    }

    /**
     * Custom validator to check if the data type is not {@link DataType#UNDEFINED}.
     * @param control The form control to validate.
     * @return Validation errors if the data type is UNDEFINED, null otherwise.
     * @private
     */
    private dataTypeNotUndefined(control: AbstractControl): ValidationErrors | null {
        return (typeof control.value === 'string') && control.value === 'UNDEFINED'
            ? {undefined: {value: control.value}}
            : null
    }

    protected get holdOutPercentageDefinition(): ConfigurationInputDefinition {
        const def = new ConfigurationInputDefinition();
        def.name = "holdOutSplitPercentage";
        def.type = ConfigurationInputType.FLOAT;
        def.label = "Percentage of rows to hold out"
        def.description = "Percentage of rows to hold out for the hold-out split. These rows will not be available in the protected dataset.";
        def.min_value = 0;
        def.max_value = 1;
        def.default_value = 0.2;
        return def;
    }

    /**
     * Enables or disabled the given form based on the value of `locked`.
     *
     * @param form The form.
     * @param locked If the form should be disabled.
     * @private
     */
    private setFormEnabled(form: FormGroup | null, locked: boolean) {
        if (form) {
            if (locked) {
                form.disable();
            } else {
                form.enable();
            }
        }
    }

    protected readonly Mode = Mode;
    protected readonly Steps = Steps;
}
