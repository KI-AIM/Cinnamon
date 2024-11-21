import { ColumnConfiguration } from "./column-configuration";

export class Statistics {
    resemblance: AttributeStatistics[];
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
    Fifth_Percentile: number;
    Q1: number;
    Median: number;
    Q3: number;
    Ninety_Fifth_Percentile: number;
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
    x_values: number[];
    density: number[];
    x_axis: string;
    y_axis: string;
}

export class FrequencyPlotData {
    frequencies: {[value: string]: number};
    x_axis: string;
    y_axis: string;
}
