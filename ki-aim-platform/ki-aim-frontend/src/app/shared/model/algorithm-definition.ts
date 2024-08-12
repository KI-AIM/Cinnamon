import { ConfigurationGroupDefinition } from "./configuration-group-definition";

export class AlgorithmDefinition {
    version: string
    type: string
    display_name: string
    description: string
    name: string
    URL: string
    configurations: {[name: string]: ConfigurationGroupDefinition}
}
