import { ConfigurationInputDefinition } from "./configuration-input-definition";

export class ConfigurationGroupDefinition {
    display_name: string
    description: string
    parameters: ConfigurationInputDefinition[]
    configurations: {[name: string]: ConfigurationGroupDefinition}
}
