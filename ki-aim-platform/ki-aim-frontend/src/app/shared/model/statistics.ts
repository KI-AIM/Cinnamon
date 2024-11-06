import { ColumnConfiguration } from "./column-configuration";

export class Statistics {
    // Date handling

    // TODO Remove wrapper objects
    // TODO not ordered by index
    resemblance: {[attribute: string]: AttributeStatistics};
    utility: any[];
}

export class AttributeStatistics {
    important_metrics: ImportantMetrics;
    details: DetailMetrics;
    plot: PlotData;
    attribute_information: ColumnConfiguration;
}

export class ImportantMetrics {
    distinct_values?: StatisticsData<number>;
    mean?: StatisticsData<number>;
    mode?: StatisticsData<string>;
    variance?: StatisticsData<number>;
    standard_deviation?: StatisticsData<number>;
    ranges?: StatisticsData<RangeMetricData>;
    missing_values_count: StatisticsData<number>;
    missing_values_percentage: StatisticsData<number>;
}

export class RangeMetricData {
    min: number;
    max: number;
}

export class DetailMetrics {
    quantiles: StatisticsData<QuantileMetricData>;
    skewness: StatisticsData<number>;
    kurtosis: StatisticsData<number>;
}

export class QuantileMetricData {
    // TODO remove space and not start with number
    "5-th Percentile": number;
    Q1: number;
    Median: number;
    Q3: number;
    "95-th Percentile": number;
}

export class PlotData {
    density?: StatisticsData<DensityPlotData>;
    frequency_count?: StatisticsData<FrequencyPlotData>;
}

export class StatisticsData<T> {
    real: T;
    synthetic: T
}

export class DensityPlotData {
    // TODO no minus
    x_values: number[];
    density: number[];
    "x-axis": string;
    "y-axis": string;
}

export class FrequencyPlotData {
    // TODO no minus
    frequencies: {[value: string]: number};
    "x-axis": string;
    "y-axis": string;
}
