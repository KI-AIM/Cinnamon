import { ConfigurationInputType } from "./configuration-input-type";

export class ConfigurationInputDefinition {
    name: string
    type: ConfigurationInputType
    label: string
    description: string
    default_value: string | number | number[]
    min_value: number | null
    max_value: number | null
    values: string[] | null
}
