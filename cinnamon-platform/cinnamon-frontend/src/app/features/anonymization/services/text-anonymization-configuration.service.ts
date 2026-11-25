import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { ConfigurationObject } from '@shared/model/anonymization-attribute-config';
import { AnonymizationMode, ModelType } from '@shared/model/text-anonymization.types';
import { catchError, map, Observable, of, shareReplay, tap } from 'rxjs';
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
    llmProfileConfiguration: LlmProfileConfiguration | null = null;
}

/** Non-secret profile settings stored with the anonymization configuration. */
export interface LlmProfileConfiguration extends ConfigurationObject {
    id: string;
    name: string;
    provider: string;
    modelName: string;
    baseUrl: string;
    endpointPath: string;
    healthcheckPath: string;
    timeoutSeconds: number;
    maxRetries: number;
    verifySsl: boolean;
    maxTokens: number;
    enableThinking: boolean;
}

/**
 * Checks the runtime shape of a profile received from the platform.
 *
 * Configuration values can come from an older saved configuration, where the
 * profile was stored as a string.  A TypeScript interface cannot protect the
 * runtime JSON value, so this guard must be used before forwarding it.
 */
export function isLlmProfileConfiguration(value: unknown): value is LlmProfileConfiguration {
    if (typeof value !== 'object' || value === null || Array.isArray(value)) {
        return false;
    }

    const profile = value as Record<string, unknown>;
    return typeof profile['id'] === 'string'
        && typeof profile['provider'] === 'string'
        && typeof profile['modelName'] === 'string'
        && typeof profile['baseUrl'] === 'string';
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
    private availableLlmProfiles: LlmProfileConfiguration[] = [];

    constructor(
        private readonly formBuilder: FormBuilder,
        private readonly http: HttpClient,
    ) {
    }

    public readonly llmProfiles$: Observable<LlmProfileConfiguration[]> = this.http.get<{
        profiles?: LlmProfileConfiguration[]
    }>(
        `${environments.apiUrl}/api/llm-profiles`,
    ).pipe(
        map(response => response.profiles ?? []),
        tap(profiles => {
            this.availableLlmProfiles = profiles;
        }),
        catchError(error => {
            this.availableLlmProfiles = [];
            console.error('Could not load LLM profiles from the backend. Check the backend logs.', error);
            return of([]);
        }),
        shareReplay({bufferSize: 1, refCount: true}),
    );

    /** Resolves a profile from the last successful backend response. */
    public resolveLlmProfile(profileName: string | null | undefined): LlmProfileConfiguration | null {
        const selected = typeof profileName === 'string' ? profileName.trim().toLowerCase() : '';
        if (!selected) {
            return null;
        }

        return this.availableLlmProfiles.find(profile =>
            profile.id.toLowerCase() === selected || profile.name.toLowerCase() === selected,
        ) ?? null;
    }

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
                value: value.llmProfile ?? '',
                disabled,
            }),
            llmProfileConfiguration: new FormControl({
                value: isLlmProfileConfiguration(value.llmProfileConfiguration)
                    ? value.llmProfileConfiguration
                    : null,
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
