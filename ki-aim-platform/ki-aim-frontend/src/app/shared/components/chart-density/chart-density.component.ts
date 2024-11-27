import {Component, Input} from '@angular/core';
import {EChartsOption} from "echarts";
import { DensityPlotData, StatisticsData } from "../../model/statistics";
import {ChartComponent, Entries} from "../chart/chart.component";
import {ColumnConfiguration} from "../../model/column-configuration";

@Component({
    selector: 'app-chart-density',
    templateUrl: '../chart/chart.component.html',
    styleUrls: ['../chart/chart.component.less'],
})
export class ChartDensityComponent extends ChartComponent {
    @Input() columnConfiguration!: ColumnConfiguration;
    @Input() data!: StatisticsData<DensityPlotData>;
    @Input() simple: boolean = false;
    @Input() syntheticSeriesLabel: string = "Synthetisch";

    private readonly colors = ['#770000', '#007700']

    protected override createChartOptions(): EChartsOption {
        const dataSetLabels: StatisticsData<string> = {
            real: "Original",
            synthetic: this.syntheticSeriesLabel,
        }

        let reference: DensityPlotData;

        const series = [];
        for (const [key, value] of Object.entries(this.data) as Entries<StatisticsData<DensityPlotData>>) {
            series.push({
                color: [this.colors[value.color_index]],
                name: dataSetLabels[key],
                type: 'line',
                symbol: 'none',
                data: value.density,
                stack: 'total',
            });
            reference = value;
        }

        const options: EChartsOption = {
            ...this.graphOptions(this.simple),
            tooltip: {
                trigger: 'axis',
                // @ts-ignore
                valueFormatter: (value) => this.formatNumber(value, null),
            },
            xAxis: {
                data: reference!.x_values,
                // name: reference!.x_axis,
                nameGap: 25,
                // show: !this.simple,
                nameLocation: 'middle',
                axisLabel: {
                    formatter: (value: string, index: number) => this.formatNumber(value, this.columnConfiguration.type),
                },
            },
            yAxis: {
                type: 'value',
                // name: reference!.y_axis,
                axisLabel: {
                    show: !this.simple,
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

        if (this.simple) {
            // @ts-ignore
            options.xAxis.axisLabel['interval'] = (index: number, value: string) => {
                const interval = Math.floor(reference!.x_values.length / 3);
                return index % interval === 0;
            };
        }

        // @ts-ignore
        options.series = series;
        return options;
    }

}
