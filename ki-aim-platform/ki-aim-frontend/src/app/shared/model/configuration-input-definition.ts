import { ConfigurationInputType } from "./configuration-input-type";

export class ConfigurationInputDefinition {
    name: string
    type: ConfigurationInputType
    label: string
    description: string
    defaultValue: string | number | number[]
    minValue: number | null
    maxValue: number | null
    values: string[] | null
}
