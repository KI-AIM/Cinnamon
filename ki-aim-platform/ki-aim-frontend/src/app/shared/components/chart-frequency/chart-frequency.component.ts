import {Component, Input} from '@angular/core';
import {EChartsOption} from "echarts";
import {FrequencyPlotData} from "../../model/statistics";
import {ChartComponent} from "../chart/chart.component";

@Component({
    selector: 'app-chart-frequency',
    templateUrl: '../chart/chart.component.html',
    styleUrls: ['../chart/chart.component.less'],
})
export class ChartFrequencyComponent extends ChartComponent {
    @Input() data!: FrequencyPlotData;

    protected override createChartOptions(): EChartsOption {
        const keys = Object.keys(this.data.frequencies);

        const options: EChartsOption = {
            ...this.graphOptions(),
            tooltip: {
            },
            xAxis: {
                type: 'category',
                data: keys,
                name: this.data["x-axis"],
                nameGap: 25,
                nameLocation: 'middle',
            },
            yAxis: {
                name: this.data["y-axis"],
                minInterval: 1,
            },
            series: [
                {
                    name: this.data["y-axis"],
                    type: 'bar',
                    data: Object.values(this.data.frequencies),
                },
            ],
        };

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
