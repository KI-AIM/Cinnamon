import { ColumnConfiguration } from "./column-configuration";
import { plainToInstance, Transform, Type } from "class-transformer";
import { TransformFnParams } from "class-transformer/types/interfaces";

export class Statistics {
    @Type(() => ResemblanceStatistics)
    resemblance: ResemblanceStatistics;

    @Type(() => UtilityStatistics)
    utility: UtilityStatistics;
}

export class ResemblanceStatistics {
    display_name: string;
    description: string;
    @Type(() => AttributeStatistics)
    attributes: AttributeStatistics[];
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

    @Type(() => StatisticsData<HistogramPlotData>)
    frequency_plot?: StatisticsData<HistogramPlotData>;
}

export class StatisticsMetaData {
    display_name: string;
    description: string;
    interpretation: string;
}

export class StatisticsValues extends StatisticsMetaData {
    @Type(() => StatisticsData<number>)
    values: StatisticsData<number>;

    @Type(() => StatisticsDifference)
    difference: StatisticsDifference;
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

export class StatisticsValuesNominal<T> extends StatisticsMetaData {
    @Type(() => StatisticsData<T>)
    values: StatisticsData<T>;
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
    x_values: number[] | string[];
    density: number[];
    x_axis: string;
    y_axis: string;
    color_index: number;
}

export class HistogramPlotData {
    @Type(() => HistogramData)
    frequencies: HistogramData[];

    x_axis: string;
    y_axis: string;
}

export class HistogramData {
    value: number;
    color_index: number;
    label: string;
}

export type StatisticsValueTypes = StatisticsValues | StatisticsValuesNominal<any>;

export class UtilityMetricData2 extends StatisticsMetaData {
    @Type(() => Predictions)
    real: Predictions;
    @Type(() => Predictions)
    synthetic: Predictions;
    @Type(() => Predictions)
    difference: Predictions;
}

export class UtilityMetricData3 extends StatisticsMetaData {
    @Type(() => UtilityData)
    predictions: UtilityData;
}

export class UtilityStatisticsData {
    classifier: string;
    score: number;
    color_index: number;
}

export class UtilityData {
    [key: string]: UtilityStatisticsData[]
}

export class Predictions {
    @Type(() => UtilityData)
    predictions: UtilityData;
}

export class UtilityStatistics {
    display_name: string;
    description: string;
    @Transform(transformUtilityMetricData, { toClassOnly: true })
    methods: UtilityMetricDataObject;
}

export type UtilityMetricDataObject = { [key: string]: UtilityMetricData2 | UtilityMetricData3 };

function transformUtilityMetricData(params: TransformFnParams): UtilityMetricDataObject {
    if (!params.value) {
        return {};
    }

    const result: UtilityMetricDataObject = {};
    for (const obj of params.value) {
        for (const [key, value] of Object.entries(obj)) {
            const type = utilityMetricDataFactory2(value);
            if (type) {
                result[key] = type;
            }
        }

    }
    return result;
}

function utilityMetricDataFactory2(data: any): UtilityMetricData2 | UtilityMetricData3 | null {
    if (!data) {
        return null;
    }

    if ('real' in data && 'synthetic' in data && 'difference' in data) {
        return plainToInstance(UtilityMetricData2, data);
    } else if ('predictions' in data) {
        return plainToInstance(UtilityMetricData3, data);
    }

    return null;
}
