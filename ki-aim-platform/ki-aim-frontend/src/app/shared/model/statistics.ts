import { ColumnConfiguration } from "./column-configuration";
import {Type} from "class-transformer";

export class Statistics {
    @Type(() => AttributeStatistics)
    resemblance: AttributeStatistics[];

    utility: any[];
}

export class AttributeStatistics {
    @Type(() => ImportantMetrics)
    important_metrics: ImportantMetrics;

    @Type(() => DetailMetrics)
    details: DetailMetrics;

    @Type(() => PlotData)
    plot: PlotData;

    @Type(() => ColumnConfiguration)
    attribute_information: ColumnConfiguration;
}

export class ImportantMetrics {
    @Type(() => StatisticsValues)
    distinct_values?: StatisticsValues;

    @Type(() => StatisticsValuesNominal<string>)
    mode?: StatisticsValuesNominal<string>;

    @Type(() => StatisticsValues)
    mean?: StatisticsValues;

    @Type(() => StatisticsValues)
    variance?: StatisticsValues;

    @Type(() => StatisticsValues)
    standard_deviation?: StatisticsValues;

    @Type(() => StatisticsValues)
    minimum?: StatisticsValues;

    @Type(() => StatisticsValues)
    maximum?: StatisticsValues;

    @Type(() => StatisticsValues)
    missing_values_count: StatisticsValues;

    @Type(() => StatisticsValues)
    missing_values_percentage: StatisticsValues;
}

export class DetailMetrics {
    @Type(() => StatisticsValues)
    fifth_percentile: StatisticsValues;

    @Type(() => StatisticsValues)
    q1: StatisticsValues;

    @Type(() => StatisticsValues)
    median: StatisticsValues;

    @Type(() => StatisticsValues)
    q3: StatisticsValues;

    @Type(() => StatisticsValues)
    ninety_fifth_percentile: StatisticsValues;

    @Type(() => StatisticsValues)
    skewness: StatisticsValues;

    @Type(() => StatisticsValues)
    kurtosis: StatisticsValues;

    @Type(() => StatisticsValuesNominal<KolmogorovSmirnovData>)
    kolmogorov_smirnov: StatisticsValuesNominal<KolmogorovSmirnovData>;

    @Type(() => StatisticsValuesNominal<HellingerDistanceData>)
    hellinger_distance: StatisticsValuesNominal<HellingerDistanceData>;
}

export class PlotData {
    @Type(() => StatisticsData<DensityPlotData>)
    density?: StatisticsData<DensityPlotData>;

    @Type(() => StatisticsData<HistogramPlotData>)
    histogram?: StatisticsData<HistogramPlotData>;

    @Type(() => StatisticsDataFrequencyPlotData)
    frequency_count?: StatisticsDataFrequencyPlotData;
}


export class StatisticsValues {
    @Type(() => StatisticsData<number>)
    values: StatisticsData<number>;

    @Type(() => StatisticsDifference)
    difference: StatisticsDifference;

    display_name: string;
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
    @Type(() => StatisticsData<T>)
    values: StatisticsData<T>;

    display_name: string;
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

export class StatisticsDataFrequencyPlotData {
    @Type(() => FrequencyPlotData)
    real: FrequencyPlotData;

    @Type(() => FrequencyPlotData)
    synthetic: FrequencyPlotData;
}

export class FrequencyPlotData {
    @Type(() => FrequenciesData)
    frequencies: FrequenciesData[];

    x_axis: string;
    y_axis: string;
}

export class HistogramPlotData {
    @Type(() => HistogramData)
    frequencies: HistogramData[];

    x_axis: string;
    y_axis: string;
}

export class FrequenciesData {
    value: number;
    color_index: number;
    category: string;
}

export class HistogramData {
    value: number;
    color_index: number;
    label: string;
}
