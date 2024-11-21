import {Component, Input} from '@angular/core';
import {EChartsOption} from "echarts";
import { FrequencyPlotData, StatisticsData} from "../../model/statistics";
import {ChartComponent} from "../chart/chart.component";

@Component({
    selector: 'app-chart-frequency',
    templateUrl: '../chart/chart.component.html',
    styleUrls: ['../chart/chart.component.less'],
})
export class ChartFrequencyComponent extends ChartComponent {
    @Input() data!: StatisticsData<FrequencyPlotData>;
    @Input() simple: boolean = false;

    private readonly limit: number = 10;

    protected override createChartOptions(): EChartsOption {
        let reference: FrequencyPlotData;

        const series = [];
        for (const [key, value] of Object.entries(this.data)) {
            const allValues: number[] = Object.values(value.frequencies);

            let displayedValues: number[];
            if (allValues.length > this.limit) {
                const sumAllValues = allValues.reduce((previousValue, currentValue) => previousValue + currentValue);

                const mostCommonValues = allValues.slice(0, this.limit);

                const sumMostCommon = mostCommonValues.reduce((previousValue, currentValue,) => previousValue + currentValue);
                mostCommonValues.push(sumAllValues - sumMostCommon);

                displayedValues = mostCommonValues;
            } else {
                displayedValues = allValues;
            }

            series.push({
                name: key,
                type: 'bar',
                data: displayedValues,
            });
            reference = value;
        }

        let displayedKeys: string[];
        const keys = Object.keys(reference!.frequencies);
        if (keys.length > this.limit) {
            const mostCommonKeys = keys.splice(0, this.limit);
            mostCommonKeys.push("Rest");
            displayedKeys = mostCommonKeys;
        } else {
            displayedKeys = keys;
        }

        const options: EChartsOption = {
            ...this.graphOptions(this.simple),
            tooltip: {
                trigger: 'axis',
                axisPointer: {
                    type: 'shadow',
                },
            },
            xAxis: {
                type: 'category',
                data: displayedKeys,
                // name: reference!.x_axis,
                nameGap: 25,
                nameLocation: 'middle',
            },
            yAxis: {
                // name: reference!.y_axis,
                minInterval: 1,
                axisLabel: {
                    show: !this.simple,
                },
            },
        };

        // @ts-ignore
        options.series = series;
        return options;
    }
}
