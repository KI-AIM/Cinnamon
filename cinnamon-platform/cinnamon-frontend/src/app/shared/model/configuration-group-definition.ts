import { ConfigurationInputDefinition } from "./configuration-input-definition";

export type ConfigurationGroupDefinitions = { [name: string]: ConfigurationGroupDefinition };

export class ConfigurationGroupDefinition {
    display_name: string
    description: string
    parameters: ConfigurationInputDefinition[]
    configurations: ConfigurationGroupDefinitions
    options: ConfigurationGroupDefinitions
}
