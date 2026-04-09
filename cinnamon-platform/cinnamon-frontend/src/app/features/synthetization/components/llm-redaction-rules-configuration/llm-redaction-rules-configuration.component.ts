import { Component, Input } from '@angular/core';
import { FormArray, FormGroup } from "@angular/forms";
import { AdditionalConfigurationGroup } from "@shared/interfaces/AdditionalConfigurationGroup";
import {
    LlmRedactionRuleConfiguration,
    LlmRedactionRulesConfigurationService
} from "../../services/llm-redaction-rules-configuration.service";

@Component({
    selector: 'app-llm-redaction-rules-configuration',
    templateUrl: './llm-redaction-rules-configuration.component.html',
    standalone: false
})
export class LlmRedactionRulesConfigurationComponent implements AdditionalConfigurationGroup {
    @Input() public disabled!: boolean;
    @Input() public form!: FormGroup;

    constructor(
        protected readonly redactionRulesConfigurationService: LlmRedactionRulesConfigurationService,
    ) {
    }

    protected get rules(): FormArray {
        return this.form.controls[this.redactionRulesConfigurationService.formGroupName] as FormArray;
    }

    protected addRule(): void {
        this.rules.push(this.redactionRulesConfigurationService.createRuleGroup(null, this.disabled));
    }

    protected addStandardPreset(): void {
        const existingRules = this.rules.getRawValue() as LlmRedactionRuleConfiguration[];
        const mergedRules = this.redactionRulesConfigurationService.mergePresetRules(
            existingRules,
            this.redactionRulesConfigurationService.getStandardIdentifierPreset(),
        );

        this.patchValue(mergedRules);
    }

    protected removeRule(index: number): void {
        this.rules.removeAt(index);
    }

    public patchValue(configs: LlmRedactionRuleConfiguration[] | null): void {
        this.rules.clear();
        const controls = this.redactionRulesConfigurationService.doSetValue(configs, this.disabled);
        controls.forEach(control => this.rules.push(control));
    }
}
