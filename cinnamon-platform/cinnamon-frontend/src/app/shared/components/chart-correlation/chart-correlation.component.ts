import { Component, Input } from '@angular/core';
import { EChartsCoreOption } from "echarts/core";
import { ChartComponent, Entries } from "src/app/shared/components/chart/chart.component";
import { ColumnConfiguration } from "src/app/shared/model/column-configuration";
import { CorrelationPlotData, StatisticsData } from "src/app/shared/model/statistics";

@Component({
    selector: 'app-chart-correlation',
    standalone: false,
    templateUrl: '../chart/chart.component.html',
    styleUrls: ['../chart/chart.component.less'],
})
export class ChartCorrelationComponent extends ChartComponent {
    @Input() public colorScheme!: string;
    @Input() public columnConfiguration!: ColumnConfiguration;
    @Input() public data!: StatisticsData<CorrelationPlotData>;
    @Input() public originalSeriesLabel: string = "Original";
    @Input() public simple: boolean = false;
    @Input() public syntheticSeriesLabel: string = "Synthetic";

    protected override createChartOptions(): EChartsCoreOption {
        const dataSetLabels: StatisticsData<string> = {
            real: this.originalSeriesLabel,
            synthetic: this.syntheticSeriesLabel,
        }

        let reference: CorrelationPlotData;

        const seriesData: Array<number[]> = [];
        const yAxis: string[] = [];

        let yIndex = 0;
        for (const [key, value] of Object.entries(this.data) as Entries<StatisticsData<CorrelationPlotData>>) {
            yAxis.push(dataSetLabels[key]);

            value.correlation_values.forEach((value, xIndex) => {
                seriesData.push([xIndex, yIndex, value]);
            });

            yIndex += 1;

            reference = value;
        }

        return {
            ...this.graphOptions(this.simple),
            tooltip: {
                // @ts-ignore
                valueFormatter: (value) => this.formatNumber(value, null),
            },
            xAxis: {
                type: 'category',
                data: reference!.x_values,
                splitArea: {
                    show: true,
                },
                nameLocation: 'middle',
                show: !this.simple,
            },
            yAxis: {
                type: 'category',
                data: yAxis,
                axisLabel: {
                    rotate: 90,
                },
                splitArea: {
                    show: true,
                },
                show: !this.simple,
            },
            visualMap: {
                min: 0,
                max: 1,
                calculable: true,
                orient: 'horizontal',
                left: 'center',
                top: -5,
                show: !this.simple,
                inRange: {
                    color: this.statisticsService.getColorScheme(this.colorScheme).slice(1),
                },
            },
            series: [
                {
                    name: 'Correlation',
                    type: 'heatmap',
                    data: seriesData,
                    label: {
                        show: !this.simple,
                        // @ts-ignore
                        formatter: (param) => this.formatNumber(param.value[2], this.columnConfiguration.type),
                    },
                    emphasis: {
                        itemStyle: {
                            shadowBlur: 10,
                            shadowColor: 'rgba(0, 0, 0, 0.5)'
                        }
                    }
                },
            ],
        };
    }
}
