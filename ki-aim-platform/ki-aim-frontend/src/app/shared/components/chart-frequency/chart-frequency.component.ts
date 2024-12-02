import {Component, Input} from '@angular/core';
import {EChartsOption} from "echarts";
import {HistogramPlotData, StatisticsData} from "../../model/statistics";
import {ChartComponent, Entries} from "../chart/chart.component";

@Component({
    selector: 'app-chart-frequency',
    templateUrl: '../chart/chart.component.html',
    styleUrls: ['../chart/chart.component.less'],
})
export class ChartFrequencyComponent extends ChartComponent {
    @Input() public data!: StatisticsData<HistogramPlotData>;
    @Input() public simple: boolean = false;
    @Input() public limit: number | null = 10;
    @Input() syntheticSeriesLabel: string = "Synthetisch";

    private readonly colors = ['#770000', '#007700']

    protected override createChartOptions(): EChartsOption {
        const dataSetLabels: StatisticsData<string> = {
            real: "Original",
            synthetic: this.syntheticSeriesLabel,
        }

        let keys: string[] | null = null;

        const series = [];
        for (const [key, value] of Object.entries(this.data) as Entries<StatisticsData<HistogramPlotData>>) {
            if (keys === null) {
                keys = value.frequencies.map(val => val.label);
            }

            const allValues: number[] = value.frequencies.map(val => val.value);

            let displayedValues: number[];
            if (this.limit && allValues.length > this.limit) {
                const sumAllValues = allValues.reduce((previousValue, currentValue) => previousValue + currentValue);

                const mostCommonValues = allValues.slice(0, this.limit);

                const sumMostCommon = mostCommonValues.reduce((previousValue, currentValue,) => previousValue + currentValue);
                mostCommonValues.push(sumAllValues - sumMostCommon);

                displayedValues = mostCommonValues;
            } else {
                displayedValues = allValues;
            }

            const displayedColors: string[] = [];
            const allColors: number[] = value.frequencies.map(val => val.color_index);
            allColors.forEach(val => {
               displayedColors.push(this.colors[val]);
            });

            series.push({
                name: dataSetLabels[key],
                type: 'bar',
                data: displayedValues,
                barMinHeight: 2,
                color: displayedColors,
            });
        }

        let displayedKeys: string[];
        if (this.limit && keys!.length > this.limit) {
            const mostCommonKeys = keys!.splice(0, this.limit);
            mostCommonKeys.push("Rest");
            displayedKeys = mostCommonKeys;
        } else {
            displayedKeys = keys!;
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
