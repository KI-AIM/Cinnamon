import { Component, Input } from '@angular/core';
import { EChartsCoreOption } from "echarts/core";
import { ChartComponent, Entries } from "src/app/shared/components/chart/chart.component";
import { CorrelationPlotData, OverallCorrelation, StatisticsData } from "src/app/shared/model/statistics";

@Component({
    selector: 'app-chart-correlation',
    standalone: false,
    templateUrl: '../chart/chart.component.html',
    styleUrls: ['../chart/chart.component.less'],
})
export class ChartCorrelationComponent extends ChartComponent {
    @Input() public colorScheme!: string;
    @Input() public data!: StatisticsData<CorrelationPlotData> | OverallCorrelation;
    @Input() public originalSeriesLabel: string = "Original";

    /**
     * If the data input is of type OverallCorrelation, determines for which dataset the correlation should be displayed.
     */
    @Input() public overallCorrelationTarget: 'real' | 'synthetic' = 'real';

    /**
     * If the chart should have margins around the chart.
     */
    @Input() public showMargins: boolean = true;
    @Input() public showVisualMap: boolean | null = null;
    @Input() public simple: boolean = false;
    @Input() public syntheticSeriesLabel: string = "Synthetic";

    protected override createChartOptions(): EChartsCoreOption {
        const plotData = (this.data instanceof OverallCorrelation)
            ? this.prepareOverallCorrelation(this.data)
            : this.prepareCorrelationPlotData(this.data);

        return {
            ...this.graphOptions(this.simple, this.showMargins),
            tooltip: {
                // @ts-ignore
                valueFormatter: (value) => {
                    return this.formatNumber(value, null);
                },
                formatter: plotData.customTooltip ? (params: any) => {
                    return this.createCustomTooltip(plotData, params);
                } : null,
            },
            xAxis: {
                type: 'category',
                data: plotData.xAxis,
                splitArea: {
                    show: true,
                },
                nameLocation: 'middle',
                show: !this.simple,
            },
            yAxis: {
                type: 'category',
                data: plotData.yAxis,
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
                show: this.showVisualMap ?? !this.simple,
                inRange: {
                    color: this.statisticsService.getColorScheme(this.colorScheme).slice(1),
                },
            },
            series: [
                {
                    name: 'Correlation',
                    type: 'heatmap',
                    data: plotData.seriesData,
                    label: {
                        show: !this.simple,
                        // @ts-ignore
                        formatter: (param) => this.formatNumber(param.value[2], null),
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

    /**
     * Converts the attribute wise correlation data from the resemblance analysis into the heatmap format.
     *
     * @param data Data to visualize.
     * @return Converted data usable for the chart.
     */
    private prepareCorrelationPlotData(data: StatisticsData<CorrelationPlotData>): PlotData {
        const dataSetLabels: StatisticsData<string> = {
            real: this.originalSeriesLabel,
            synthetic: this.syntheticSeriesLabel,
        }

        let reference: CorrelationPlotData;

        const seriesData: Array<number[]> = [];
        const yAxis: string[] = [];

        let yIndex = 0;
        for (const [key, value] of Object.entries(data) as Entries<StatisticsData<CorrelationPlotData>>) {
            yAxis.push(dataSetLabels[key]);

            value.correlation_values.forEach((value, xIndex) => {
                seriesData.push([xIndex, yIndex, value]);
            });

            yIndex += 1;

            reference = value;
        }

        return {
            seriesData: seriesData,
            xAxis: reference!.x_values,
            yAxis: yAxis,
            customTooltip: false,
        }
    }

    /**
     * Converts the overall correlation data from the statistics overview into the heatmap format.
     *
     * @param data Data to visualize.
     * @return Converted data usable for the chart.
     */
    private prepareOverallCorrelation(data: OverallCorrelation): PlotData {
        const seriesData: Array<number[]> = [];

        const target = data[this.overallCorrelationTarget];

        for (let xIndex = 0; xIndex < target.length; xIndex++) {
            for (let yIndex = 0; yIndex < target[xIndex].length; yIndex++) {
                seriesData.push([xIndex, yIndex, target[xIndex][yIndex]]);
            }
        }

        return {
            seriesData: seriesData,
            xAxis: data.labels,
            yAxis: data.labels,
            customTooltip: true,
        }
    }

    /**
     * Generates the custom tooltip showing the x-label and y-label.
     *
     * @param plotData The data that is visualized.
     * @param params Params injected by ECharts formatter function.
     * @returns The tooltip content as HTML.
     * @private
     */
    private createCustomTooltip(plotData: PlotData, params: any) {
        const xValue = plotData.xAxis[params.data[0]];
        const yValue = plotData.yAxis[params.data[1]];
        const value = this.formatNumber(params.data[2], null);

        // Layout is stolen from the HTML of a chart
        // This should not be modified so it looks like the original tooltip
        return `<div style="margin: 0 0 0;line-height:1;"><div style="font-size:14px;color:#666;font-weight:400;line-height:1;">${params.seriesName}</div><div style="margin: 10px 0 0;line-height:1;"><div style="margin: 0 0 0;line-height:1;">${params.marker}<span style="font-size:14px;color:#666;font-weight:400;margin-left:2px">${xValue} - ${yValue}</span><span style="float:right;margin-left:20px;font-size:14px;color:#666;font-weight:900">${value}</span><div style="clear:both"></div></div><div style="clear:both"></div></div><div style="clear:both"></div></div>`
    }
}

/**
 * Data required for visualizing the heatmap.
 */
interface PlotData {
    /**
     * Series data to visualize.
     * The nested array has three values, the x-position, the y-position and the value.
     */
    seriesData: Array<number[]>;
    /**
     * Labels for the x-axis.
     * Also shown in the tooltip when hovering over a cell.
     */
    xAxis: string[];
    /**
     * Labels for the y-axis.
     */
    yAxis: string[];
    /**
     * If the custom tooltip showing the x-label and y-label should be used.
     */
    customTooltip: boolean;
}
