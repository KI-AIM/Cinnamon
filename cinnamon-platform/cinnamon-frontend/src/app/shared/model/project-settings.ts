import { Type } from "class-transformer";

export class ProjectSettings {
    projectName: string;
    contactMail: string | null;
    contactUrl: string | null;
    reportCreator: string | null;

    @Type(() => MetricSettings)
    metricConfiguration: MetricSettings;
}

export class MetricSettings  {
    colorScheme: string = "Default";

    useUserDefinedImportance: boolean;
    userDefinedImportance: MetricImportanceDefinition;
}

export type MetricImportanceDefinition = {
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
        label: 'Hide',
        value: 1,
    }
}
