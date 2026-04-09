import { Injectable } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, Validators } from "@angular/forms";
import { ConfigurationObject } from "@shared/model/anonymization-attribute-config";

export interface LlmRedactionRuleConfiguration extends ConfigurationObject {
    name: string;
    replacement_token: string;
}

const HIPAA_GDPR_DIRECT_IDENTIFIER_PRESET: LlmRedactionRuleConfiguration[] = [
    {name: "Name", replacement_token: "[NAME]"},
    {name: "Address", replacement_token: "[ADDRESS]"},
    {name: "Contact Information", replacement_token: "[CONTACT]"},
    {name: "Identification Number", replacement_token: "[IDENTIFIER]"},
];

const INDIRECT_IDENTIFIER_PRESET: LlmRedactionRuleConfiguration[] = [
    {name: "Age", replacement_token: "[AGE]"},
    {name: "Date", replacement_token: "[DATE]"},
    {name: "Gender", replacement_token: "[GENDER]"},
    {name: "Postalcode", replacement_token: "[POSTAL_CODE]"},
    {name: "City", replacement_token: "[CITY]"},
    {name: "Institution", replacement_token: "[INSTITUTION]"},
    {name: "Occupation", replacement_token: "[OCCUPATION]"},
];

const STANDARD_IDENTIFIER_PRESET: LlmRedactionRuleConfiguration[] = [
    ...HIPAA_GDPR_DIRECT_IDENTIFIER_PRESET,
    ...INDIRECT_IDENTIFIER_PRESET,
];

@Injectable({
    providedIn: 'root',
})
export class LlmRedactionRulesConfigurationService {
    public readonly formGroupName = 'redaction_rules';

    constructor(
        private readonly formBuilder: FormBuilder,
    ) {
    }

    public initForm(form: FormGroup, configs: LlmRedactionRuleConfiguration[] | null, disabled: boolean): void {
        const controls = this.doSetValue(configs, disabled);
        form.addControl(this.formGroupName, new FormArray(controls));
    }

    public doSetValue(configs: LlmRedactionRuleConfiguration[] | null, disabled: boolean): FormGroup[] {
        if (configs == null) {
            return [];
        }

        return configs.map(config => this.createRuleGroup(config, disabled));
    }

    public createRuleGroup(config: Partial<LlmRedactionRuleConfiguration> | null, disabled: boolean): FormGroup {
        return this.formBuilder.group({
            name: [{value: config?.name ?? '', disabled}, [Validators.required]],
            replacement_token: [{value: config?.replacement_token ?? '[REDACTED]', disabled}, [Validators.required]],
        });
    }

    public getStandardIdentifierPreset(): LlmRedactionRuleConfiguration[] {
        return STANDARD_IDENTIFIER_PRESET.map(rule => ({...rule}));
    }

    public mergePresetRules(
        existingRules: LlmRedactionRuleConfiguration[],
        presetRules: LlmRedactionRuleConfiguration[],
    ): LlmRedactionRuleConfiguration[] {
        const existingNames = new Set(existingRules.map(rule => this.normalizeRuleName(rule.name)));
        const merged = [...existingRules];

        presetRules.forEach(rule => {
            const normalizedName = this.normalizeRuleName(rule.name);
            if (!existingNames.has(normalizedName)) {
                merged.push({...rule});
                existingNames.add(normalizedName);
            }
        });

        return merged;
    }

    private normalizeRuleName(name: string): string {
        return name.trim().toLowerCase();
    }
}
