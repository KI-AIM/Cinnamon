import {Component, Input} from '@angular/core';
import {EChartsOption} from "echarts";
import {DensityPlotData, StatisticsData} from "../../model/statistics";
import {ChartComponent} from "../chart/chart.component";
import {ColumnConfiguration} from "../../model/column-configuration";

@Component({
    selector: 'app-chart-density',
    templateUrl: '../chart/chart.component.html',
    styleUrls: ['../chart/chart.component.less'],
})
export class ChartDensityComponent extends ChartComponent {
    @Input() columnConfiguration!: ColumnConfiguration;
    @Input() data!: StatisticsData<DensityPlotData>;

    protected override createChartOptions(): EChartsOption {
        let reference: DensityPlotData;

        const series = [];
        for (const [key, value] of Object.entries(this.data)) {
            series.push({
                name: key,
                type: 'line',
                symbol: 'none',
                data: value.density,
                stack: 'total',
            });
            reference = value;
        }

        const options: EChartsOption = {
            ...this.graphOptions(),
            tooltip: {
                trigger: 'axis',
                // @ts-ignore
                valueFormatter: (value) => this.formatNumber(value, null),
            },
            xAxis: {
                data: reference!.x_values,
                name: reference!["x-axis"],
                nameGap: 25,
                nameLocation: 'middle',
                axisLabel: {
                    formatter: (value: string, index: number) => this.formatNumber(value, this.columnConfiguration.type),
                },
            },
            yAxis: {
                type: 'value',
                name: reference!["y-axis"],
                axisLabel: {
                    formatter: (value: any) => this.formatNumber(value, null),
                },
            },
            axisPointer: {
                label: {
                    // @ts-ignore
                    formatter: (params) => this.formatNumber(params.value, this.columnConfiguration.type),
                }
            }
        };
        // @ts-ignore
        options.series = series;
        return options;
    }

}
