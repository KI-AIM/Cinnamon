import { Component, Input } from "@angular/core";
import { EChartsCoreOption } from "echarts/core";
import { ChartComponent, Entries } from "../chart/chart.component";
import { ColumnConfiguration } from "../../model/column-configuration";
import { HistogramPlotData, StatisticsData } from "../../model/statistics";
import "echarts-wordcloud";

@Component({
    selector: "app-chart-wordcloud",
    templateUrl: "../chart/chart.component.html",
    styleUrls: ["../chart/chart.component.less"],
    standalone: false
})
export class ChartWordcloudComponent extends ChartComponent {
    @Input() public colorScheme!: string;
    @Input() public columnConfiguration!: ColumnConfiguration;
    @Input() public data!: StatisticsData<HistogramPlotData>;
    @Input() public originalSeriesLabel: string = "Original";
    @Input() public simple: boolean = false;
    @Input() public syntheticSeriesLabel: string = "Synthetic";

    protected override createChartOptions(): EChartsCoreOption {
        const dataSetLabels: StatisticsData<string> = {
            real: this.originalSeriesLabel,
            synthetic: this.syntheticSeriesLabel,
        };

        const hasSynthetic = !!this.data.synthetic;
        const series: any[] = [];
        const topPadding = this.simple ? 42 : 38;
        const cloudHeight = hasSynthetic ? "84%" : "88%";

        for (const [key, value] of Object.entries(this.data) as Entries<StatisticsData<HistogramPlotData>>) {
            const index = key === "real" ? 0 : 1;
            const left = hasSynthetic ? (index === 0 ? "2%" : "52%") : "1%";
            const width = hasSynthetic ? "46%" : "98%";

            const words = value.frequencies.map((frequency) => ({
                name: frequency.label,
                value: frequency.value,
                textStyle: {
                    color: this.statisticsService.getColorScheme(this.colorScheme)[frequency.color_index],
                },
            }));

            series.push({
                type: "wordCloud",
                shape: "square",
                left,
                top: topPadding,
                width,
                height: cloudHeight,
                sizeRange: this.simple ? [18, 56] : (hasSynthetic ? [20, 64] : [22, 72]),
                rotationRange: [-45, 45],
                rotationStep: 45,
                gridSize: hasSynthetic ? 9 : 7,
                drawOutOfBound: false,
                shrinkToFit: true,
                textStyle: {
                    fontFamily: "sans-serif",
                },
                emphasis: {
                    textStyle: {
                        shadowBlur: 8,
                        shadowColor: "#333",
                    },
                },
                data: words,
            });
        }

        const chartName = this.createChartName("Wordcloud", this.data, this.columnConfiguration, dataSetLabels);

        return {
            ...this.graphOptions(this.simple, chartName),
            grid: {
                show: false,
            },
            tooltip: {
                trigger: "item",
                formatter: (params: any) => {
                    const value = this.statisticsService.formatNumber(params.value, {
                        dataType: null,
                        max: 3,
                        min: 3,
                        unit: "%",
                    });
                    return `${params.name}: ${value}`;
                },
            },
            series,
        };
    }
}
