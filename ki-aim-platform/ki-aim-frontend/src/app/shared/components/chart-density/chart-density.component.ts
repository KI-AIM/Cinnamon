import {Component, Input} from '@angular/core';
import {EChartsOption} from "echarts";
import {DensityPlotData} from "../../model/statistics";
import {ChartComponent} from "../chart/chart.component";

@Component({
    selector: 'app-chart-density',
    templateUrl: '../chart/chart.component.html',
    styleUrls: ['../chart/chart.component.less'],
})
export class ChartDensityComponent extends ChartComponent {
    @Input() data!: DensityPlotData;

    protected override createChartOptions(): EChartsOption {
        return {
            ...this.graphOptions(),
            // @ts-ignore
            tooltip: {
                valueFormatter: (value: number, dataIndex: number) => value.toFixed(3),
            },
            xAxis: {
                data: this.data.x_values,
                name: this.data["x-axis"],
                nameGap: 25,
                nameLocation: 'middle',
                axisLabel: {
                    formatter: (value: string, index: number) => parseFloat(value).toFixed(3),
                },
            },
            yAxis: {
                name: this.data["y-axis"],
            },
            series: [
                {
                    name: this.data["y-axis"],
                    type: 'line',
                    data: this.data.density,
                },
            ],
        };
    }

}
