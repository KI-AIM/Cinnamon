import { Type } from "class-transformer";

export class ProjectSettings {
    @Type(() => MetricSettings)
    metricConfiguration: MetricSettings;
}

export class MetricSettings  {
    [key: string]: MetricImportance;
}

export enum MetricImportance {
    IMPORTANT = "IMPORTANT",
    ADDITIONAL = "ADDITIONAL",
    NOT_RELEVANT = "NOT_RELEVANT",
}

export const MetricImportanceData: { [key in MetricImportance]: { label: string, value: number } } = {
    IMPORTANT: {
        label: 'Important',
        value: 3,
    },
    ADDITIONAL: {
        label: 'Additional',
        value: 2,
    },
    NOT_RELEVANT: {
        label: 'Not Relevant',
        value: 1,
    }
}
