import { ConfigurationInputDefinition } from "./configuration-input-definition";

export class AlgorithmDefinition {
    synthesizer: string
    version: string
    type: string
    display_name: string
    description: string
    URL: string
    model_parameter: ConfigurationInputDefinition[]
    data: ConfigurationInputDefinition[]
    model_fitting: ConfigurationInputDefinition[]
    sampling: ConfigurationInputDefinition[]
}
