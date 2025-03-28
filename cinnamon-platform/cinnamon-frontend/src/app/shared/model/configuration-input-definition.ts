import { ConfigurationInputType } from "./configuration-input-type";

export class ConfigurationInputDefinition {
    name: string
    type: ConfigurationInputType
    label: string
    description: string
    default_value: string | number | number[]
    invert: string | null
    min_value: number | null
    max_value: number | null
    values: string[] | null
    switch: Array<{
        depends_on: string;
        conditions: Array<{
            if: string;
            values: string[] | number[];
            min_value?: number;
            max_value?: number;
        }>;
    }> | null
}
