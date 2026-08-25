import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { AdditionalConfigurationGroup } from '@shared/interfaces/AdditionalConfigurationGroup';
import { DataType } from '@shared/model/data-type';
import { DataConfigurationService } from '@shared/services/data-configuration.service';
import { Subscription } from 'rxjs';
import { TextAnonymizationConfigurationService } from '../../services/text-anonymization-configuration.service';
import { AnonymizationMode, ModelType } from '@shared/model/text-anonymization.types';
import { ConfigurationInputDefinition } from '@shared/model/configuration-input-definition';
import { ConfigurationInputType } from '@shared/model/configuration-input-type';

@Component({
    selector: 'app-text-anonymization-configuration',
    templateUrl: './text-anonymization-configuration.component.html',
    standalone: false,
})
export class TextAnonymizationConfigurationComponent implements AdditionalConfigurationGroup, OnInit, OnDestroy {
    @Input() public disabled!: boolean;
    @Input() public form!: FormGroup;
    protected readonly AnonymizationMode = AnonymizationMode;
    protected readonly modelTypes: ModelType[] = Object.values(ModelType);

    protected readonly modelTypeInfo: ConfigurationInputDefinition = {
        name: 'modelType',
        type: ConfigurationInputType.STRING,
        label: 'Model',
        description: 'Select the text-anonymization model to use.',
        default_value: ModelType.XLM_ROBERTA,
        mandatory: true,
        invert: null,
        min_value: null,
        max_value: null,
        values: this.modelTypes,
        switch: null,
    };

    protected readonly textColumnsInfo: ConfigurationInputDefinition = {
        name: 'columns',
        type: ConfigurationInputType.ATTRIBUTE_LIST,
        label: 'Text columns',
        description: 'Select which TEXT columns should be anonymized. All TEXT columns are selected by default.',
        default_value: 'All available text columns',
        mandatory: false,
        invert: null,
        min_value: null,
        max_value: null,
        values: null,
        switch: null,
    };

    protected readonly confidenceThresholdInfo: ConfigurationInputDefinition = {
        name: 'confidenceThreshold',
        type: ConfigurationInputType.FLOAT,
        label: 'Detection confidence threshold',
        description: 'Only entities at or above this confidence threshold are transformed.',
        default_value: 0.9,
        mandatory: true,
        invert: null,
        min_value: 0,
        max_value: 1,
        values: null,
        switch: null,
    };

    protected readonly anonymizationModeInfo: ConfigurationInputDefinition = {
        name: 'anonymizationMode',
        type: ConfigurationInputType.STRING,
        label: 'Anonymization mode',
        description: 'Redaction replaces text with an entity label; pseudonymization uses a substitute.',
        default_value: AnonymizationMode.Redact,
        mandatory: true,
        invert: null,
        min_value: null,
        max_value: null,
        values: Object.values(AnonymizationMode),
        switch: null,
    };

    protected textColumns: Array<{name: string, index: number}> = [];
    private dataConfigurationSubscription?: Subscription;
    private textColumnsInitialized = false;

    constructor(
        private readonly dataConfigurationService: DataConfigurationService,
        protected readonly textAnonymizationConfigurationService: TextAnonymizationConfigurationService,
    ) {
    }

    public ngOnInit(): void {
        this.dataConfigurationSubscription = this.dataConfigurationService.dataConfiguration$.subscribe(configuration => {
            this.textColumns = configuration.configurations
                .filter(column => column.type === DataType.TEXT)
                .map(column => ({name: column.name, index: column.index}));
            this.selectAllTextColumnsIfEmpty();
        });
    }

    public ngOnDestroy(): void {
        this.dataConfigurationSubscription?.unsubscribe();
    }

    public patchValue(config: any): void {
        this.textConfigGroup.patchValue(
            this.textAnonymizationConfigurationService.createGroup(config, this.disabled).getRawValue(),
            {emitEvent: false},
        );
        this.selectAllTextColumnsIfEmpty();
    }

    protected get textConfigGroup(): FormGroup {
        return this.form.controls[this.textAnonymizationConfigurationService.formGroupName] as FormGroup;
    }

    protected resetToDefault(
        field: 'modelType' | 'columns' | 'confidenceThreshold' | 'anonymizationMode',
    ): void {
        const defaults = {
            modelType: ModelType.XLM_ROBERTA,
            columns: this.textColumns.map(column => column.name),
            confidenceThreshold: 0.9,
            anonymizationMode: AnonymizationMode.Redact,
        };

        this.textConfigGroup.get(field)?.setValue(defaults[field]);
    }

    private selectAllTextColumnsIfEmpty(): void {
        if (this.textColumnsInitialized || !this.form || this.textColumns.length === 0) {
            return;
        }

        const columnsControl = this.textConfigGroup.get('columns');
        const selectedColumns = columnsControl?.value;
        if (!columnsControl) {
            return;
        }
        if (!Array.isArray(selectedColumns) || selectedColumns.length === 0) {
            columnsControl.setValue(this.textColumns.map(column => column.name), {emitEvent: false});
        }
        this.textColumnsInitialized = true;
    }
}
