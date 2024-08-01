import { ConfigurationGroupDefinition } from "./configuration-group-definition";

export class AlgorithmDefinition {
    synthesizer: string
    version: string
    type: string
    display_name: string
    description: string
    name: string
    URL: string
    arguments: {[name: string]: ConfigurationGroupDefinition}
}
