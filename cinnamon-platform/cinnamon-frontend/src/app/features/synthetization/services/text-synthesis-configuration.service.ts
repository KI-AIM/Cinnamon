import { Injectable } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, Validators } from "@angular/forms";
import { ConfigurationObject } from "@shared/model/anonymization-attribute-config";

@Injectable({
    providedIn: 'root',
})
export class TextSynthesisConfigurationService {
    public readonly formGroupName = "text_synthesis_configuration";

    constructor(
        private readonly formBuilder: FormBuilder,
    ) {
    }

    public initForm(form: FormGroup, config: ConfigurationObject | null, disabled: boolean): void {
        const group = this.createGroup(config as any, disabled);
        form.addControl(this.formGroupName, group);
    }

    public createGroup(config: any, disabled: boolean): FormGroup {
        const algorithm = config?.synthetization_configuration?.algorithm ?? {};
        const llmProfile = algorithm.llm_profile ?? {};
        const modelParameter = algorithm.model_parameter ?? {};
        const modelFitting = algorithm.model_fitting ?? {};
        const sampling = algorithm.sampling ?? {};

        return this.formBuilder.group({
            synthetization_configuration: this.formBuilder.group({
                algorithm: this.formBuilder.group({
                    synthesizer: new FormControl({value: algorithm.synthesizer ?? "llm_text_synthesis", disabled}, [Validators.required]),
                    llm_profile: this.formBuilder.group({
                        llm_profile: new FormControl({value: llmProfile.llm_profile ?? "", disabled}),
                    }),
                    model_parameter: this.formBuilder.group({
                        profile_rows: new FormControl({value: modelParameter.profile_rows ?? 1000, disabled}, [Validators.required, Validators.min(1)]),
                        few_shot_rows: new FormControl({value: modelParameter.few_shot_rows ?? 20, disabled}, [Validators.required, Validators.min(0)]),
                    }),
                    model_fitting: this.formBuilder.group({
                        user_prompt_domain_context: new FormControl({value: modelFitting.user_prompt_domain_context ?? "", disabled}),
                        allow_structured_corrections: new FormControl({value: modelFitting.allow_structured_corrections ?? true, disabled}, [Validators.required]),
                    }),
                    sampling: this.formBuilder.group({
                        temperature: new FormControl({value: sampling.temperature ?? 0.3, disabled}, [Validators.required, Validators.min(0), Validators.max(2)]),
                        top_p: new FormControl({value: sampling.top_p ?? 0.9, disabled}, [Validators.required, Validators.min(0), Validators.max(1)]),
                    }),
                }),
            }),
        });
    }
}
