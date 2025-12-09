import { Component, OnChanges, OnInit } from '@angular/core';
import { ColumnConfiguration } from "@shared/model/column-configuration";
import { StatisticsData } from "@shared/model/statistics";
import {DataType} from "../../model/data-type";
import { StatisticsService } from "../../services/statistics.service";
import { EChartsCoreOption, EChartsType } from "echarts/core";

@Component({
    selector: 'app-chart',
    templateUrl: './chart.component.html',
    styleUrls: ['./chart.component.less'],
    standalone: false
})
export class ChartComponent implements OnInit, OnChanges {
    protected options: EChartsCoreOption;
    protected chartInstances: EChartsType;
    protected zoomInstance: EChartsType | null = null;
    protected zoom: EChartsCoreOption | null = null;

    constructor(
        protected statisticsService: StatisticsService,
    ) {
    }

    ngOnInit() {
        this.options = this.createChartOptions();
    }

    ngOnChanges() {
        this.options = this.createChartOptions();
    }

    protected onChartInit(event: EChartsType) {
        this.chartInstances = event;
        this.afterChartInit();
    }

    protected afterChartInit(): void {
    }

    protected onZoomInit(event: EChartsType) {
        this.zoomInstance = event;
        this.afterZoomInit();
    }

    protected afterZoomInit(): void {
    }

    protected createChartOptions(): EChartsCoreOption {
        return {};
    }

    public get dataUrl(): string | null {
        return this.chartInstances
            ? this.chartInstances.getDataURL({
                type: 'jpeg', pixelRatio: 2,
                backgroundColor: '#fff',
            })
            : null;
    }

    private downloadGraph() {
        const imageUrl = this.chartInstances.getDataURL({
            type: 'jpeg',
            pixelRatio: 2,
            backgroundColor: '#fff',
        });
        this.downloadImage(imageUrl);
    }

    private downloadImage(dataUrl: string): void {
        const a = document.createElement('a');
        a.href = dataUrl;
        a.download = '-mean.jpeg';
        a.click();
    }

    /**
     * Creates common chart options for all charts.
     *
     * @param simple If the chart should remove space for the axis.
     * @param name   The name of the cart.
     * @param options Additional options for the chart.
     * @return An object containing the ECharts options.
     * @protected
     */
    protected graphOptions(simple: boolean, name: string, options: ChartOptions = {}): EChartsCoreOption {
        const margins  = options.showMargins ?? true;

        return {
            grid: {
                left: margins ? (simple ? 25 : 70) : 0,
                top: margins ? 33 : 0,
                right: margins ? 30 : 0,
                bottom: margins ? (simple ? 20 : 50) : 0,
                borderWidth: 1,
                borderColor: '#ccc',
                show: true
            },
            toolbox: {
                show: options.showToolbox ?? true,
                feature: {
                    saveAsImage: {
                        type: 'png',
                        name: name,
                    },
                },
                iconStyle: {
                    borderColor: '#506d85',
                },
                emphasis: {
                    iconStyle: {
                        borderColor: '#7d92a5',
                        textPosition: 'left',
                    },
                },
                right: 245,
                top: 2,
            },
        };
    }

    protected formatNumber(value: number | string, dataType: DataType | null): string {
        return this.statisticsService.formatNumber(value, {dataType, max: 3, min: 3});
    }

    /**
     * Creates the chart name based on the chart type, displayed attribute and the labels of the visualized data.
     *
     * @param chart  The type of the chart.
     * @param data   The data visualized in the chart.
     * @param config The attribute configuration for the corresponding attribute.
     * @param labels Labels for the displayed data.
     * @return The chart name.
     * @protected
     */
    protected createChartName(chart: string, data: StatisticsData<any>, config: ColumnConfiguration | null, labels: StatisticsData<string>): string {
        let chartName = "";

        if (data.real) {
            chartName += labels.real + " ";
        }
        if (data.synthetic) {
            if (data.real) {
                chartName += "vs ";
            }
            chartName += labels.synthetic + " ";
        }
        if (config) {
            chartName += config.name + " ";
        }
        chartName += chart;
        return chartName;
    }
}

interface ChartOptions {
    /**
     * If the chart should have margins.
     * Default: true
     */
    showMargins?: boolean;
    /**
     * If a toolbox should be shown. Contains the download button.
     * Default: true
     */
    showToolbox?: boolean;
}

// Stolen from https://stackoverflow.com/questions/60141960/typescript-key-value-relation-preserving-object-entries-type
export type Entries<T> = {
    [K in keyof T]: [K, T[K]];
}[keyof T][];
