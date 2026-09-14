import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, QueryList, ViewChildren, ChangeDetectionStrategy } from '@angular/core';
import {
    AbstractControl,
    FormArray,
    FormBuilder,
    FormGroup,
    ValidationErrors,
    ValidatorFn,
    Validators
} from "@angular/forms";
import { Mode } from "@core/enums/mode";
import { LockedInformation, StateManagementService } from "@core/services/state-management.service";
import { noSpaceValidator } from "@shared/directives/no-space-validator.directive";
import { ConfigurationInputDefinition } from "@shared/model/configuration-input-definition";
import { ConfigurationInputType } from "@shared/model/configuration-input-type";
import { AttributeImportOverview, MissingAttribute } from "@shared/model/attribute-import-overview";
import { ColumnConfiguration } from "@shared/model/column-configuration";
import { DataScale, DataScaleMetadata } from "@shared/model/data-scale";
import { DataSetInfo } from "@shared/model/data-set-info";
import { DataType } from "@shared/model/data-type";
import { DateFormatConfiguration } from "@shared/model/date-format-configuration";
import { DateTimeFormatConfiguration } from "@shared/model/date-time-format-configuration";
import { FileInformation } from "@shared/model/file-information";
import { RangeConfiguration } from "@shared/model/range-configuration";
import { Status } from "@shared/model/status";
import { StringPatternConfiguration } from "@shared/model/string-pattern-configuration";
import { TextLanguageConfiguration } from "@shared/model/text-language-configuration";
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
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
export class DataConfigurationComponent implements OnInit {
    protected attributeConfigurationForm: FormGroup;
    protected dataSetConfigurationForm: FormGroup | null;

    protected isAdvanceConfigurationExpanded: boolean = false;

    /**
     * Attributes only present in the configuration, they are ignored.
     */
    protected configOnlyAttributes: ColumnConfiguration[] = [];
    /**
     * Attributes only present in the data, they must be configured before confirming.
     */
    protected dataOnlyAttributes: MissingAttribute[] = [];

    protected pageData$: Observable<{
        dataConfiguration: DataConfiguration,
        dataSetInfo: DataSetInfo | null,
        fileInformation: FileInformation,
        fileType: FileType | null,
        locked: LockedInformation,
        status: Status,
    }>

    /**
     * Confidences of the estimated configurations of added attributes indexed by the attribute index.
     */
    private suggestionConfidences = new Map<number, number>();
    private fileType: FileType | null = null;

    @ViewChildren('attributeConfiguration') attributeConfigurations: QueryList<AttributeConfigurationComponent>;

    constructor(
        public configuration: DataConfigurationService,
        public dataService: DataService,
        private dataSetInfoService: DataSetInfoService,
        private readonly errorHandlingService: ErrorHandlingService,
        public fileService: FileService,
        private readonly formBuilder: FormBuilder,
        private readonly statusService: StatusService,
        public loadingService: LoadingService,
        private readonly stateManagementService: StateManagementService,
    ) {
    }

    private get locked(): boolean {
        return this.statusService.isStepCompleted(Steps.VALIDATION)
    }

    ngOnInit(): void {

        this.pageData$ = combineLatest({
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
            fileInformation: this.fileService.fileInfo$,
            fileType: this.fileService.fileInfo$.pipe(
                map(fileInformation => fileInformation.type),
            ),
            locked: this.stateManagementService.currentStepLocked$.pipe(
                tap(value => {
                    this.setFormEnabled(this.dataSetConfigurationForm, value.isLocked);
                    this.setFormEnabled(this.attributeConfigurationForm, value.isLocked);
                }),
            ),
            status: this.statusService.statusNonNull$,
        }).pipe(
            switchMap(value => {
                return this.configuration.attributeMatching$.pipe(
                    map(overview => {
                        this.initAttributeMatching(overview);
                        const dataConfiguration = new DataConfiguration();
                        dataConfiguration.configurations = overview.importConfiguration.attributes;
                        return dataConfiguration;
                    }),
                    tap(dataConfiguration => {
                        this.fileType = value.fileType;
                        if (this.configuration.localDataConfiguration !== null) {
                            this.attributeConfigurationForm = this.createAttributeConfigurationForm(this.configuration.localDataConfiguration, value.fileType);
                        } else {
                            this.setEmptyColumnNames(dataConfiguration);
                            this.attributeConfigurationForm = this.createAttributeConfigurationForm(dataConfiguration, value.fileType);
                        }

                        // Show errors on page load
                        this.attributeConfigurationForm.markAllAsTouched();

                        this.attributeConfigurationForm.valueChanges.pipe(debounceTime(300), distinctUntilChanged()).subscribe(value1 => {
                            this.configuration.localDataConfiguration = plainToInstance(DataConfiguration, value1);
                        });
                    }),
                    map(dataConfiguration => {
                        return {
                            ...value,
                            dataConfiguration: dataConfiguration,
                        }
                    }),
                );
            }),
        );
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

    protected get isAttributeConfigurationInvalid(): boolean {
        return this.attributeConfigurationForm.invalid || this.dataOnlyAttributes.length > 0;
    }

    protected get isInvalid(): boolean {
        return this.isAttributeConfigurationInvalid || this.isDataSetConfigurationFormInvalid;
    }

    /**
     * Returns the confidence of the estimation for the attribute with the given index.
     * @param attributeIndex The index of the attribute in the data.
     * @return The confidence.
     * @protected
     */
    protected getConfidence(attributeIndex: number): number | null {
        if (this.configuration.confidence && attributeIndex < this.configuration.confidence.length) {
            return this.configuration.confidence[attributeIndex];
        }
        return this.suggestionConfidences.get(attributeIndex) ?? null;
    }

    /**
     * Adds the configuration for an attribute only present in the data.
     * Uses the estimated configuration if available.
     * @param attribute The missing attribute.
     */
    protected addDataOnlyAttribute(attribute: MissingAttribute): void {
        const columnConfiguration = attribute.suggestedConfiguration
            ? plainToInstance(ColumnConfiguration, structuredClone(attribute.suggestedConfiguration))
            : Object.assign(new ColumnConfiguration(), {type: DataType.UNDEFINED, configurations: []});
        columnConfiguration.index = attribute.index;
        columnConfiguration.name = this.getAttributeName(attribute);

        if (attribute.confidence !== null) {
            this.suggestionConfidences.set(attribute.index, attribute.confidence);
        }

        this.removeDataOnlyAttribute(attribute);
        this.insertColumnConfiguration(columnConfiguration);
    }

    /**
     * Uses the configuration of an ignored attribute for an attribute only present in the data.
     * @param configOnly The ignored attribute configuration.
     * @param dataOnly The attribute in the data.
     */
    protected assignConfigOnlyAttribute(configOnly: ColumnConfiguration, dataOnly: MissingAttribute): void {
        const columnConfiguration = plainToInstance(ColumnConfiguration, structuredClone(configOnly));
        columnConfiguration.index = dataOnly.index;
        if (dataOnly.name !== "") {
            columnConfiguration.name = dataOnly.name;
        }

        this.removeConfigOnlyAttribute(configOnly);
        this.removeDataOnlyAttribute(dataOnly);
        this.insertColumnConfiguration(columnConfiguration);
    }

    /**
     * Discards an ignored attribute configuration.
     * @param attribute The ignored attribute.
     */
    protected removeConfigOnlyAttribute(attribute: ColumnConfiguration): void {
        this.configOnlyAttributes = this.configOnlyAttributes.filter(a => a !== attribute);
        this.configuration.localConfigOnlyAttributes = this.configOnlyAttributes;
    }

    /**
     * Returns the display name of an attribute in the data.
     */
    protected getAttributeName(attribute: MissingAttribute): string {
        return attribute.name !== "" ? attribute.name : 'column_' + attribute.index;
    }

    confirmConfiguration() {
        const config = plainToInstance(DataConfiguration, this.attributeConfigurationForm.getRawValue());
        this.loadingService.setLoadingStatus(true);
        this.configuration.storeAttributeMatching(config.configurations, this.configOnlyAttributes).pipe(
            switchMap(() => this.dataService.storeData(config)),
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
        dataConfiguration.configurations.forEach(column => {
            if (column.name === undefined || column.name === null || column.name == "") {
                column.name = 'column_' + column.index;
            }
        });
    }

    /**
     * Initializes the lists of unmatched attributes from the local state or the given overview.
     */
    private initAttributeMatching(overview: AttributeImportOverview): void {
        this.configOnlyAttributes = this.configuration.localConfigOnlyAttributes ?? overview.configOnlyAttributes;
        this.dataOnlyAttributes = this.configuration.localDataOnlyAttributes ?? overview.dataOnlyAttributes;
    }

    private removeDataOnlyAttribute(attribute: MissingAttribute): void {
        this.dataOnlyAttributes = this.dataOnlyAttributes.filter(a => a !== attribute);
        this.configuration.localDataOnlyAttributes = this.dataOnlyAttributes;
    }

    /**
     * Inserts the form for the given column configuration, so the forms stay ordered by the attribute index.
     */
    private insertColumnConfiguration(columnConfiguration: ColumnConfiguration): void {
        const formArray = this.attributeConfigurationForm.controls['configurations'] as FormArray;
        let position = formArray.controls.findIndex(c => c.get('index')!.value > columnConfiguration.index);
        if (position === -1) {
            position = formArray.length;
        }

        const group = this.createColumnConfigurationForm(columnConfiguration, this.fileType);
        group.markAllAsTouched();
        formArray.insert(position, group);
        this.checkUniqueColumnNames();
    }

    private handleUpload() {
        this.loadingService.setLoadingStatus(false);
        this.dataSetInfoService.invalidateCache();

        this.stateManagementService.setAndRouteToStep(Steps.VALIDATION).subscribe();
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

        const configurationsFrom = (this.attributeConfigurationForm.controls['configurations'] as FormArray).controls
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

        // Add errors to inputs with duplicate column names
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

    private createAttributeConfigurationForm(dataConfiguration: DataConfiguration, fileType: FileType | null): FormGroup {
        const formArray = dataConfiguration.configurations.map(
            columnConfiguration => this.createColumnConfigurationForm(columnConfiguration, fileType));
        return this.formBuilder.group({configurations: this.formBuilder.array(formArray)});
    }

    private createColumnConfigurationForm(columnConfiguration: ColumnConfiguration, fileType: FileType | null): FormGroup {
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
            } else if (addConfig.getName() === "TextLanguageConfiguration") {
                const textLanguageConfiguration = addConfig as TextLanguageConfiguration;
                addConfigs.push(
                    this.formBuilder.group({
                        name: ["TextLanguageConfiguration"],
                        language: [{
                            value: textLanguageConfiguration.language,
                            disabled: this.locked
                        }, {
                            validators: [Validators.required]
                        }],
                    })
                );
            }
        }

        const columnGroup = this.formBuilder.group({
            index: [columnConfiguration.index],
            name: [{value: columnConfiguration.name, disabled: this.locked || fileType === FileType.FHIR}, {
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
        }, {validators: [this.validateScale(), this.validateDateFormat()]});

        return columnGroup;
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

    /**
     * Validates that the data scale is applicable to the selected data type.
     * Has to be applied to the parent form group.
     *
     * @return The validator function.
     */
    private validateScale(): ValidatorFn {
        return (control: AbstractControl): ValidationErrors | null => {
            const typeControl = control.get('type')!;
            const scaleControl = control.get('scale')!;

            const dataType = typeControl.value as DataType;
            const dataScale = scaleControl.value as DataScale;

            if (dataType == null || dataScale == null) {
                return null;
            }

            const error = DataScaleMetadata[dataScale].applicableTo.includes(dataType)
                ? null
                : {scale: {scale: dataScale, type: dataType}};

            scaleControl.setErrors(error);
            scaleControl.markAsTouched();

            return error;
        }
    }

    private validateDateFormat(): ValidatorFn {
        return (control: AbstractControl): ValidationErrors | null => {
            const dataType = control.get('type')!.value as DataType;

            if (dataType == null || dataType === DataType.UNDEFINED) {
                return null;
            }

            if (dataType === DataType.DATE) {
                const additionalConfigurations = control.get('configurations') as FormArray;

                let hasFormat = false;
                for (const additionalConfiguration of additionalConfigurations.controls) {
                    const nameControl = additionalConfiguration.get('name')!;
                    if (nameControl.value === "DateFormatConfiguration") {
                        hasFormat = true;
                        break;
                    }
                }

                if (!hasFormat) {
                    const error = {missingDateFormat: true};
                    additionalConfigurations.setErrors(error)
                    return error;
                }
            } else if (dataType === DataType.DATE_TIME) {
                const additionalConfigurations = control.get('configurations') as FormArray;

                let hasFormat = false;
                for (const additionalConfiguration of additionalConfigurations.controls) {
                    const nameControl = additionalConfiguration.get('name')!;
                    if (nameControl.value === "DateTimeFormatConfiguration") {
                        hasFormat = true;
                        break;
                    }
                }

                if (!hasFormat) {
                    const error = {missingDateTimeFormat: true};
                    additionalConfigurations.setErrors(error)
                    return error;
                }
            }

            return null;
        }
    }

    protected readonly FileType = FileType;
    protected readonly Mode = Mode;
    protected readonly Steps = Steps;
}
