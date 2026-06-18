import { FormBuilder, FormGroup } from "@angular/forms";
import { AlgorithmDefinition } from "@shared/model/algorithm-definition";
import { ConfigurationInputType } from "@shared/model/configuration-input-type";
import { DataConfiguration } from "@shared/model/data-configuration";
import { TextSynthesisConfigurationService } from "./text-synthesis-configuration.service";

describe("TextSynthesisConfigurationService", () => {
    let service: TextSynthesisConfigurationService;
    let dataConfiguration: DataConfiguration;

    beforeEach(() => {
        service = new TextSynthesisConfigurationService(new FormBuilder());
        dataConfiguration = new DataConfiguration();
    });

    it("creates the free-text root group with synthesizer selection", () => {
        const root = new FormGroup({});

        service.initForm(root, null, false);

        const algorithmGroup = root.get("text_synthesis_configuration.synthetization_configuration.algorithm") as any;
        expect(algorithmGroup).not.toBeNull();
        expect(algorithmGroup?.get("synthesizer")?.value).toBe("llm_nearest_neighbor_few_shot_text_synthesis");
        expect(Object.keys(algorithmGroup.controls)).toEqual(["synthesizer"]);
    });

    it("keeps stored free-text synthesizer values when recreating the root group", () => {
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

    it("syncs only the parameters defined by the selected free-text algorithm", () => {
        const root = new FormGroup({});
        service.initForm(root, {
            synthetization_configuration: {
                algorithm: {
                    synthesizer: "llm_nearest_neighbor_few_shot_text_synthesis",
                    llm_profile: {
                        llm_profile: "Local Ollama",
                    },
                    model_parameter: {
                        few_shot_rows: 2,
                        similarity_strategy: "Attributes",
                        knowledge_source_type: "NOT_IMPLEMENTED",
                    },
                    model_fitting: {
                        user_prompt_domain_context: "",
                        allow_structured_corrections: true,
                    },
                    sampling: {
                        temperature: 0.3,
                        top_p: 0.9,
                    },
                },
            },
        } as any, false);

        service.syncFormWithDefinition(root, createFewShotDefinition(), dataConfiguration, false);

        const algorithmGroup = root.get("text_synthesis_configuration.synthetization_configuration.algorithm") as any;
        expect(algorithmGroup.get("llm_profile.llm_profile")?.value).toBe("Local Ollama");
        expect(algorithmGroup.get("model_parameter.few_shot_rows")?.value).toBe(2);
        expect(algorithmGroup.get("model_parameter.similarity_strategy")?.value).toBe("Attributes");
        expect(algorithmGroup.get("model_parameter.knowledge_source_type")).toBeNull();
        expect(algorithmGroup.get("model_fitting.allow_structured_corrections")?.value).toBeTrue();
        expect(algorithmGroup.get("sampling.temperature")?.value).toBe(0.3);
    });

    it("includes knowledge_source_type when the YAML definition contains it", () => {
        const root = new FormGroup({});
        service.initForm(root, {
            synthetization_configuration: {
                algorithm: {
                    synthesizer: "llm_nearest_neighbor_knowledge_grounded_text_synthesis",
                    model_parameter: {
                        knowledge_source_type: "NOT_IMPLEMENTED",
                    },
                },
            },
        } as any, false);

        service.syncFormWithDefinition(root, createKnowledgeGroundedDefinition(), dataConfiguration, false);

        const algorithmGroup = root.get("text_synthesis_configuration.synthetization_configuration.algorithm") as any;
        expect(algorithmGroup.get("model_parameter.knowledge_source_type")?.value).toBe("NOT_IMPLEMENTED");
    });
});

function createFewShotDefinition(): AlgorithmDefinition {
    return createDefinition(false);
}

function createKnowledgeGroundedDefinition(): AlgorithmDefinition {
    return createDefinition(true);
}

function createDefinition(includeKnowledgeSourceType: boolean): AlgorithmDefinition {
    return {
        name: "free_text",
        version: "0.1",
        type: "cross-sectional",
        URL: "/free-text",
        display_name: "Free Text",
        description: "",
        parameters: [],
        options: {},
        configurations: {
            llm_profile: {
                display_name: "LLM Profile",
                description: "",
                parameters: [
                    {
                        name: "llm_profile",
                        type: ConfigurationInputType.STRING,
                        label: "LLM Profile",
                        description: "",
                        default_value: "",
                        mandatory: true,
                        invert: null,
                        min_value: null,
                        max_value: null,
                        values: [],
                        switch: null,
                    },
                ],
                configurations: {},
                options: {},
            },
            model_parameter: {
                display_name: "Model Parameters",
                description: "",
                parameters: [
                    {
                        name: "few_shot_rows",
                        type: ConfigurationInputType.INTEGER,
                        label: "Few-Shot Rows",
                        description: "",
                        default_value: 20,
                        mandatory: true,
                        invert: null,
                        min_value: 0,
                        max_value: 200,
                        values: null,
                        switch: null,
                    },
                    {
                        name: "similarity_strategy",
                        type: ConfigurationInputType.STRING,
                        label: "Similarity Strategy",
                        description: "",
                        default_value: "Random",
                        mandatory: true,
                        invert: null,
                        min_value: null,
                        max_value: null,
                        values: ["Random", "Attributes"],
                        switch: null,
                    },
                    ...(includeKnowledgeSourceType ? [{
                        name: "knowledge_source_type",
                        type: ConfigurationInputType.STRING,
                        label: "Knowledge Source Type",
                        description: "",
                        default_value: "NOT_IMPLEMENTED",
                        mandatory: true,
                        invert: null,
                        min_value: null,
                        max_value: null,
                        values: ["NOT_IMPLEMENTED"],
                        switch: null,
                    }] : []),
                ],
                configurations: {},
                options: {},
            },
            model_fitting: {
                display_name: "Model Fitting",
                description: "",
                parameters: [
                    {
                        name: "user_prompt_domain_context",
                        type: ConfigurationInputType.STRING,
                        label: "Domain Context",
                        description: "",
                        default_value: "",
                        mandatory: false,
                        invert: null,
                        min_value: null,
                        max_value: null,
                        values: null,
                        switch: null,
                    },
                    {
                        name: "allow_structured_corrections",
                        type: ConfigurationInputType.BOOLEAN,
                        label: "Allow Structured Corrections",
                        description: "",
                        default_value: true,
                        mandatory: true,
                        invert: null,
                        min_value: null,
                        max_value: null,
                        values: null,
                        switch: null,
                    },
                ],
                configurations: {},
                options: {},
            },
            sampling: {
                display_name: "Sampling",
                description: "",
                parameters: [
                    {
                        name: "temperature",
                        type: ConfigurationInputType.FLOAT,
                        label: "Temperature",
                        description: "",
                        default_value: 0.3,
                        mandatory: true,
                        invert: null,
                        min_value: 0,
                        max_value: 2,
                        values: null,
                        switch: null,
                    },
                    {
                        name: "top_p",
                        type: ConfigurationInputType.FLOAT,
                        label: "Top-p",
                        description: "",
                        default_value: 0.9,
                        mandatory: true,
                        invert: null,
                        min_value: 0,
                        max_value: 1,
                        values: null,
                        switch: null,
                    },
                ],
                configurations: {},
                options: {},
            },
        },
    } as AlgorithmDefinition;
}
