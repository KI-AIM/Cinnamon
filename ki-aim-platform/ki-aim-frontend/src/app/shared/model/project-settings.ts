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

export const MetricImportanceData: { [key in MetricImportance]: { label: string } } = {
    IMPORTANT: {
        label: 'Important',
    },
    ADDITIONAL: {
        label: 'Additional',
    },
    NOT_RELEVANT: {
        label: 'Not Relevant',
    }
}
