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
    distinct_values?: StatisticsValues;
    mode?: StatisticsValuesNominal<string>;
    mean?: StatisticsValues;
    variance?: StatisticsValues;
    standard_deviation?: StatisticsValues;
    minimum?: StatisticsValues;
    maximum?: StatisticsValues;
    missing_values_count: StatisticsValues;
    missing_values_percentage: StatisticsValues;
}

export class DetailMetrics {
    fifth_percentile: StatisticsValues;
    q1: StatisticsValues;
    median: StatisticsValues;
    q3: StatisticsValues;
    ninety_fifth_percentile: StatisticsValues;
    skewness: StatisticsValues;
    kurtosis: StatisticsValues;
    kolmogorov_smirnov: StatisticsValuesNominal<KolmogorovSmirnovData>;
    hellinger_distance: StatisticsValuesNominal<HellingerDistanceData>;
}

export class PlotData {
    density?: StatisticsData<DensityPlotData>;
    // histogram?: StatisticsData<HistogramPlotData>;
    histogram?: StatisticsData<FrequencyPlotData>;
    frequency_count?: StatisticsData<FrequencyPlotData>;
}


export class StatisticsValues {
    values: StatisticsData<number>;
    difference: StatisticsDifference;
    description: string;
    interpretation: string;
}

export class KolmogorovSmirnovData {
    KS_statistic: number;
    p_value: number;
    color_index: number;
}

export class HellingerDistanceData {
    hellinger_distance: number;
    color_index: number;
}

export class StatisticsValuesNominal<T> {
    values: StatisticsData<T>;
    description: string;
    interpretation: string;
}

export class StatisticsDifference {
    absolute: number;
    percentage: number;
    color_index: number;
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
    color_index: number;
}

export class FrequencyPlotData {
    frequencies: {[value: string]: FrequenciesData};
    x_axis: string;
    y_axis: string;
}

export class HistogramPlotData {
    frequencies: [{ [value: string]: FrequenciesData }];
    x_axis: string;
    y_axis: string;
}

export class FrequenciesData {
    value: number;
    color_index: number;
}
