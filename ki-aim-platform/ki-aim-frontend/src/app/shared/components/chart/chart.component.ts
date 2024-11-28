import {Component, OnInit} from '@angular/core';
import {ECharts, EChartsOption} from "echarts";
import {DataType} from "../../model/data-type";
import { StatisticsService } from "../../services/statistics.service";

@Component({
    selector: 'app-chart',
    templateUrl: './chart.component.html',
    styleUrls: ['./chart.component.less']
})
export class ChartComponent implements OnInit {
    protected options: EChartsOption;
    protected chartInstances: ECharts;
    protected zoomInstance: ECharts | null = null;
    protected zoom: EChartsOption | null = null;

    constructor(
        protected statisticsService: StatisticsService,
    ) {
    }

    ngOnInit() {
        this.options = this.createChartOptions();
    }

    protected onChartInit(event: ECharts) {
        this.chartInstances = event;
        this.afterChartInit();
    }

    protected afterChartInit(): void {
    }

    protected onZoomInit(event: ECharts) {
        this.zoomInstance = event;
        this.afterZoomInit();
    }

    protected afterZoomInit(): void {
    }

    protected createChartOptions(): EChartsOption {
        return {};
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

    protected graphOptions(simple: boolean): EChartsOption {
        return {
            grid: {
                left: simple ? 25 : 70,
                top: simple ? 0 : 20,
                right: 30,
                bottom: simple ? 20 : 50,
                borderWidth: 1,
                borderColor: '#ccc',
                show: true
            },
        };

    }

    protected formatNumber(value: number | string, dataType: DataType | null): string {
        return this.statisticsService.formatNumber(value, {dataType});
    }
}

// Stolen from https://stackoverflow.com/questions/60141960/typescript-key-value-relation-preserving-object-entries-type
export type Entries<T> = {
    [K in keyof T]: [K, T[K]];
}[keyof T][];
