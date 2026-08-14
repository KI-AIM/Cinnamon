import { Injectable } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { ConfigurationObject } from '@shared/model/anonymization-attribute-config';
import {AnonymizationMode, ModelType} from '@shared/model/text-anonymization.types'

/**
 * Configuration consumed by the text-anonymization worker.
 * An empty column list deliberately means all columns whose data type is TEXT.
 */



export class TextAnonymizationConfiguration extends ConfigurationObject {
    columns: string[] = [];
    confidenceThreshold: number = 0.90;
    anonymizationMode: AnonymizationMode = AnonymizationMode.Redact;
    modelType: ModelType = ModelType.XLM_ROBERTA;
}

/**
 * Builds the form for the text-anonymization worker's configuration.
 */
@Injectable({
    providedIn: 'root',
})
export class TextAnonymizationConfigurationService {
    public readonly formGroupName = 'textAnonymizationConfiguration';

    constructor(private readonly formBuilder: FormBuilder) {
    }

    public initForm(form: FormGroup, config: Partial<TextAnonymizationConfiguration> | null, disabled: boolean): void {
        form.addControl(this.formGroupName, this.createGroup(config, disabled));
    }

    public createGroup(config: Partial<TextAnonymizationConfiguration> | null, disabled: boolean): FormGroup {
        const value = config ?? {};

        return this.formBuilder.group({

            modelType: new FormControl({value: value.modelType ?? ModelType.XLM_ROBERTA, disabled}, [Validators.required]),
            columns: new FormControl({value: value.columns ?? [], disabled}),
            confidenceThreshold: new FormControl(
                {value: value.confidenceThreshold ?? 0.90, disabled},
                [Validators.required, Validators.min(0), Validators.max(1)],
            ),
            anonymizationMode: new FormControl({
                    value: value.anonymizationMode ?? AnonymizationMode.Redact,
                    disabled,
                }, Validators.required,
            ),
        });
    }
}
