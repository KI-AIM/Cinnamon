import { Component, Input, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { AdditionalConfigurationGroup } from "@shared/interfaces/AdditionalConfigurationGroup";
import { ColumnConfiguration } from 'src/app/shared/model/column-configuration';
import { DataConfiguration } from 'src/app/shared/model/data-configuration';
import { DataConfigurationService } from 'src/app/shared/services/data-configuration.service';
import { AnonymizationAttributeConfigurationService } from '../../services/anonymization-attribute-configuration.service';
import { MatSelect } from '@angular/material/select';
import { AnonymizationAttributeRowConfiguration, AttributeProtection, } from 'src/app/shared/model/anonymization-attribute-config';
import { Subscription } from "rxjs";
import { AbstractControl, FormArray, FormBuilder, FormGroup, ValidationErrors, Validators } from '@angular/forms';

@Component({
    selector: 'app-anonymization-attribute-configuration',
    templateUrl: './anonymization-attribute-configuration.component.html',
    styleUrls: ['./anonymization-attribute-configuration.component.less'],
    standalone: false
})
export class AnonymizationAttributeConfigurationComponent implements OnInit, OnDestroy, AdditionalConfigurationGroup {
    @Input() public disabled!: boolean;
    @Input() public form!: FormGroup;

    @ViewChild("attributeDropdown") attributeDropdown: MatSelect;

    dataConfiguration: DataConfiguration;

    private dataConfigurationSubscription: Subscription;

    constructor(
        public configuration: DataConfigurationService,
        public attributeConfigurationService: AnonymizationAttributeConfigurationService,
        private formBuilder: FormBuilder,
    ) {
    }

    /**
     * Key for the form group of this configuration component.
     */
    static get formGroupName(): string {
        return 'attributeConfiguration';
    }

    /**
     * Initializes the given form to be used by this component.
     * @param form The form to be initialized.
     * @param configs The initial configuration.
     * @param disabled If the form is disabled initially.
     */
    static initForm(form: FormGroup, configs: AnonymizationAttributeRowConfiguration[] | null, disabled: boolean): void {
        form.addControl(AnonymizationAttributeConfigurationComponent.formGroupName, new FormArray([], [Validators.required, AnonymizationAttributeConfigurationComponent.hasGeneralization]));
        AnonymizationAttributeConfigurationComponent.doSetValue(configs, new FormBuilder(), AnonymizationAttributeConfigurationComponent.getAttributeConfigurationFormArray(form), disabled);
    }

    ngOnInit() {
        this.dataConfigurationSubscription = this.configuration.dataConfiguration$.subscribe(value => {
            this.dataConfiguration = value;
        });
    }

    ngOnDestroy() {
        this.dataConfigurationSubscription.unsubscribe();
    }

    /**
     * Checks if the form has at least one attribute configuration.
     */
    protected hasAttributeConfiguration(): boolean {
        return this.getAttributeConfigurationForms(this.form).length > 0;
    }

    /**
     * Filters the dataConfiguration ColumnConfiguration list
     * by removing all entries with attributes that are currently
     * used in the anonymization attribute configuration
     * @returns Array<ColumnConfiguration>
     */
    protected getAvailableConfigurations() {
        const indicesAlreadyUsed = new Set(this.getAttributeConfigurationForms(this.form).map(item => item.controls['index'].value));

        if (this.dataConfiguration !== undefined && this.dataConfiguration !== null) {
            return this.dataConfiguration.configurations.filter(item => !indicesAlreadyUsed.has(item.index))
        } else {
            return new Array<ColumnConfiguration>();
        }
    }

    /**
     * Returns the available column configurations
     * sorted by their index attribute
     * @returns Array<ColumnConfiguration>
     */
    protected getAvailableConfigurationsSortedById() {
        return this.getAvailableConfigurations().sort((a, b) => a.index - b.index);
    }

    /**
     * Event that is triggered by selecting another
     * column in the dropdown.
     *
     * Adds a new entry to the anonymization attribute
     * configuration, thus removing it from the dropdown
     * automatically.
     * @param value
     */
    protected onSelectionChange(value: any) {
        const selectedRowIndex = value;
        const selectedRow = this.getAvailableConfigurations().find(
            (row) => row.index === selectedRowIndex
        );

        if (selectedRow !== null && selectedRow !== undefined) {
            this.attributeDropdown.value = null;
            this.addAttributeConfigurationRow(selectedRow);
        }
    }

    addAllAttributes() {
        this.getAvailableConfigurations().forEach(selectedRow => {
            if (selectedRow !== null && selectedRow !== undefined) {
                this.addAttributeConfigurationRow(selectedRow);
            }
        });
    }

    /**
     * Sets the value of the form.
     * Used by {@link ConfigurationGroupComponent#patchComponents}.
     *
     * @param configs
     */
    public patchValue(configs: AnonymizationAttributeRowConfiguration[]) {
        this.removeAllAttributes();
        AnonymizationAttributeConfigurationComponent.doSetValue(configs, this.formBuilder, this.getAttributeConfigurationFormArray(this.form), this.disabled);
    }

    public static doSetValue(configs: AnonymizationAttributeRowConfiguration[] | null, formBuilder: FormBuilder, form: FormArray, disabled: boolean) {
        if (configs == null) {
            return;
        }
        configs.forEach(config => {
            const group = formBuilder.group({
                attributeProtection: [{value: config.attributeProtection, disabled: disabled}, [Validators.required]],
                dataType: [{value: config.dataType, disabled: true}, [Validators.required]],
                index: [config.index, [Validators.required]],
                intervalSize: [{value: config.intervalSize, disabled: disabled}, [Validators.required]],
                name: [{value: config.name, disabled: true}, [Validators.required]],
                scale: [{value: config.scale, disabled: true}, [Validators.required]],
            });

            form.push(group);
        });
    }

    private addAttributeConfigurationRow(selectedRow: ColumnConfiguration) {
        const defaultAttributeProtection = this.attributeConfigurationService.getDefaultAttributeProtection(selectedRow.scale, selectedRow.type);
        const defaultIntervalSize = this.attributeConfigurationService.getDefaultIntervalSize(defaultAttributeProtection, selectedRow.scale);
        const defaultIntervalDisabled = this.attributeConfigurationService.getDefaultIntervalDisabled(defaultAttributeProtection);

        const group = this.formBuilder.group({
            attributeProtection: [{value: defaultAttributeProtection, disabled: this.disabled}, [Validators.required]],
            dataType: [{value: selectedRow.type, disabled: true}, [Validators.required]],
            index: [selectedRow.index, [Validators.required]],
            intervalSize: [{
                value: defaultIntervalSize,
                disabled: this.disabled || defaultIntervalDisabled
            }, [Validators.required]],
            name: [{value: selectedRow.name, disabled: true}, [Validators.required]],
            scale: [{value: selectedRow.scale, disabled: true}, [Validators.required]],
        });
        this.getAttributeConfigurationFormArray(this.form).push(group);
    }

    protected getAttributeConfigurationFormArray(form: FormGroup): FormArray {
        return form.controls[AnonymizationAttributeConfigurationComponent.formGroupName] as FormArray;
    }

    private static getAttributeConfigurationFormArray(form: FormGroup): FormArray {
        return form.controls[AnonymizationAttributeConfigurationComponent.formGroupName] as FormArray;
    }

    protected getAttributeConfigurationForms(form: FormGroup): FormGroup[] {
        return this.getAttributeConfigurationFormArray(form).controls as FormGroup[];
    }

    /**
     * Removes all attribute configurations.
     * @protected
     */
    protected removeAllAttributes() {
        this.getAttributeConfigurationFormArray(this.form).clear();
    }

    /**
     * Event to remove a given anonymization attribute configuration
     * @param index to delete.
     */
    protected removeAttributeConfigurationRow(index: number) {
        this.getAttributeConfigurationFormArray(this.form).removeAt(index);
    }


    /**
     * Custom validator to check if at least one attribute is protected by a generalization method.
     * An empty array is considered valid.
     * @param control The form array to validate.
     * @return Validation errors if no generalization protection is available, null otherwise.
     * @private
     */
    private static hasGeneralization(control: AbstractControl): ValidationErrors | null {
        if (!(control instanceof FormArray)) {
            return null;
        }

        if (control.controls.length === 0) {
            return null;
        }

        for (const row of control.controls) {
            const protection = (row as FormGroup).controls['attributeProtection'].value;
            if (AnonymizationAttributeConfigurationComponent.isGeneralization(protection)) {
                return null;
            }
        }

        return {noGeneralization: {}};
    }

    private static isGeneralization(protection: string) {
        return protection === AttributeProtection.MASKING ||
            protection === AttributeProtection.GENERALIZATION ||
            protection === AttributeProtection.MICRO_AGGREGATION ||
            protection === AttributeProtection.RECORD_DELETION ||
            protection === AttributeProtection.DATE_GENERALIZATION;
    }

}
