import {Component, Input} from '@angular/core';
import {HistogramPlotData, StatisticsData} from "../../model/statistics";
import {ChartComponent, Entries} from "../chart/chart.component";
import { EChartsCoreOption } from "echarts/core";

@Component({
    selector: 'app-chart-frequency',
    templateUrl: '../chart/chart.component.html',
    styleUrls: ['../chart/chart.component.less'],
    standalone: false
})
export class ChartFrequencyComponent extends ChartComponent {
    @Input() public colorScheme!: string;
    @Input() public data!: StatisticsData<HistogramPlotData>;
    @Input() public simple: boolean = false;
    @Input() public limit: number | null = 10;
    @Input() public originalSeriesLabel: string = "Original";
    @Input() syntheticSeriesLabel: string = "Synthetic";

    protected override createChartOptions(): EChartsCoreOption {
        const dataSetLabels: StatisticsData<string> = {
            real: this.originalSeriesLabel,
            synthetic: this.syntheticSeriesLabel,
        }

        let keys: string[] | null = null;

        const series = [];
        for (const [key, value] of Object.entries(this.data) as Entries<StatisticsData<HistogramPlotData>>) {
            if (keys === null) {
                keys = value.frequencies.map(val => val.label);
            }

            const allValues: number[] = value.frequencies.map(val => val.value);

            let displayed: Array<{ value: number, itemStyle: { color: string } }> = [];
            if (this.limit && allValues.length > this.limit) {
                displayed = value.frequencies.slice(0, this.limit).map(val => {
                    return {
                        value: val.value,
                        itemStyle: {
                            color: this.statisticsService.getColorScheme(this.colorScheme)[val.color_index],
                        }
                    }
                });

                const sumAllValues = allValues.reduce((previousValue, currentValue) => previousValue + currentValue);
                const sumMostCommon = displayed.reduce((previousValue, currentValue,) => previousValue + currentValue.value, 0);
                displayed.push({
                        value: sumAllValues - sumMostCommon,
                        itemStyle: {
                            color: this.statisticsService.getColorScheme(this.colorScheme)[5],
                        }
                    })
            } else {
                displayed = value.frequencies.map(val => {
                   return {
                       value: val.value,
                       itemStyle: {
                           color: this.statisticsService.getColorScheme(this.colorScheme)[val.color_index],
                       }
                   }
                });
            }

            const displayedColors: string[] = [];
            const allColors: number[] = value.frequencies.map(val => val.color_index);
            allColors.forEach(val => {
               displayedColors.push(this.statisticsService.getColorScheme(this.colorScheme)[val]);
            });

            series.push({
                name: dataSetLabels[key],
                type: 'bar',
                data: displayed,
                barGap: '-70%',
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

        const options: EChartsCoreOption = {
            ...this.graphOptions(this.simple),
            tooltip: {
                trigger: 'axis',
                axisPointer: {
                    type: 'shadow',
                },
                // @ts-ignore
                valueFormatter: (value) => this.statisticsService.formatNumber(value, {dataType: null, max: 3, min: 3, unit: "%"})
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
                    formatter: '{value}%'
                },
            },
        };

        // @ts-ignore
        options.series = series;
        return options;
    }
}
