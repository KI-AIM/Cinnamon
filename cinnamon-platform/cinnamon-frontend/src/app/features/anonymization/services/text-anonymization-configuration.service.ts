import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { ConfigurationObject } from '@shared/model/anonymization-attribute-config';
import { AnonymizationMode, ModelType } from '@shared/model/text-anonymization.types';
import { catchError, map, Observable, of, shareReplay } from 'rxjs';
import { environments } from 'src/environments/environment';

/**
 * Configuration consumed by the text-anonymization worker.
 * An empty column list deliberately means all columns whose data type is TEXT.
 */



export class TextAnonymizationConfiguration extends ConfigurationObject {
    enabled: boolean = true;
    columns: string[] = [];
    confidenceThreshold: number = 0.90;
    anonymizationMode: AnonymizationMode = AnonymizationMode.Redact;
    modelType: ModelType = ModelType.XLM_ROBERTA;
    llmProfile: string = '';
}

/**
 * Builds the form for the text-anonymization worker's configuration.
 */
@Injectable({
    providedIn: 'root',
})
export class TextAnonymizationConfigurationService {
    public readonly piiFormGroupName = 'piiTextAnonymizationConfiguration';
    public readonly sensitiveFormGroupName = 'sensitiveTextAnonymizationConfiguration';

    constructor(
        private readonly formBuilder: FormBuilder,
        private readonly http: HttpClient,
    ) {
    }

    public readonly llmProfiles$: Observable<string[]> = this.http.get<{
        profiles?: Array<{name: string}>
    }>(
        `${environments.apiUrl}/api/llm-profiles`,
    ).pipe(
        map(response => (response.profiles ?? []).map(profile => profile.name)),
        catchError(() => of([])),
        shareReplay({bufferSize: 1, refCount: true}),
    );

    public initForm(
        form: FormGroup,
        config: Partial<TextAnonymizationConfiguration> | null,
        disabled: boolean,
        formGroupName: string = this.piiFormGroupName,
        defaultEnabled = true,
    ): void {
        form.addControl(formGroupName, this.createGroup(config, disabled, defaultEnabled));
    }

    public createGroup(
        config: Partial<TextAnonymizationConfiguration> | null,
        disabled: boolean,
        defaultEnabled = true,
    ): FormGroup {
        const value = config ?? {};

        return this.formBuilder.group({
            enabled: new FormControl({value: value.enabled ?? defaultEnabled, disabled}),
            modelType: new FormControl({value: value.modelType ?? ModelType.XLM_ROBERTA, disabled}, [Validators.required]),
            llmProfile: new FormControl({
                value: value.llmProfile ?? (value as any).llm_profile ?? '',
                disabled,
            }),
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
