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

    protected override createChartOptions(): EChartsOption {
        let reference: FrequencyPlotData;

        const series = [];
        for (const [key, value] of Object.entries(this.data)) {
            series.push({
                name: key,
                type: 'bar',
                data: Object.values(value.frequencies),
            });
            reference = value;
        }

        const keys = Object.keys(reference!.frequencies);

        const options: EChartsOption = {
            ...this.graphOptions(),
            tooltip: {
                trigger: 'axis',
                axisPointer: {
                    type: 'shadow',
                },
            },
            xAxis: {
                type: 'category',
                data: keys,
                name: reference!["x-axis"],
                nameGap: 25,
                nameLocation: 'middle',
            },
            yAxis: {
                name: reference!["y-axis"],
                minInterval: 1,
            },
        };

        // @ts-ignore
        options.series = series;

        if (keys.length > 10) {
            options.dataZoom = [
                {
                    type: 'slider',
                    show: true,
                    xAxisIndex: [0],
                    start: 0,
                    end: (10 / keys.length) * 100,
                },
            ];
        }

        return options;
    }
}
