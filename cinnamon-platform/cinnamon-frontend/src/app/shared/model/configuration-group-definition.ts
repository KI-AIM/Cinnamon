import { ConfigurationInputDefinition } from "./configuration-input-definition";

export type ConfigurationGroupDefinitions = { [name: string]: ConfigurationGroupDefinition };

export class ConfigurationGroupDefinition {
    display_name: string
    description: string
    disclaimer?: string
    visualization_type?: VisualizationType
    parameters: ConfigurationInputDefinition[]
    configurations: ConfigurationGroupDefinitions
    options: ConfigurationGroupDefinitions
}

export enum VisualizationType {
    DETAILS = "details",
    IMPORTANT_METRICS = "important_metrics",
    PLOT = "plot",
}
