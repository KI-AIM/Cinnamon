import { FormBuilder, FormGroup } from "@angular/forms";
import { TextSynthesisConfigurationService } from "./text-synthesis-configuration.service";

describe("TextSynthesisConfigurationService", () => {
    let service: TextSynthesisConfigurationService;

    beforeEach(() => {
        service = new TextSynthesisConfigurationService(new FormBuilder());
    });

    it("creates controls for all free-text synthesizer YAML parameters", () => {
        const root = new FormGroup({});

        service.initForm(root, null, false);

        const algorithmGroup = root.get("text_synthesis_configuration.synthetization_configuration.algorithm") as any;
        expect(algorithmGroup).not.toBeNull();
        expect(algorithmGroup?.get("synthesizer")?.value).toBe("llm_nearest_neighbor_few_shot_text_synthesis");
        expect(algorithmGroup?.get("llm_profile.llm_profile")).not.toBeNull();
        expect(algorithmGroup?.get("model_parameter.profile_rows")).not.toBeNull();
        expect(algorithmGroup?.get("model_parameter.few_shot_rows")).not.toBeNull();
        expect(algorithmGroup?.get("model_parameter.similarity_strategy")).not.toBeNull();
        expect(algorithmGroup?.get("model_parameter.knowledge_source_type")).not.toBeNull();
        expect(algorithmGroup?.get("model_fitting.user_prompt_domain_context")).not.toBeNull();
        expect(algorithmGroup?.get("model_fitting.allow_structured_corrections")).not.toBeNull();
        expect(algorithmGroup?.get("sampling.temperature")).not.toBeNull();
        expect(algorithmGroup?.get("sampling.top_p")).not.toBeNull();
    });

    it("keeps stored free-text synthesizer values when recreating the group", () => {
        const group = service.createGroup({
            synthetization_configuration: {
                algorithm: {
                    synthesizer: "llm_nearest_neighbor_knowledge_grounded_text_synthesis",
                },
            },
        }, false);

        const algorithmGroup = group.get("synthetization_configuration.algorithm") as any;
        expect(algorithmGroup?.get("synthesizer")?.value).toBe("llm_nearest_neighbor_knowledge_grounded_text_synthesis");
    });
});
