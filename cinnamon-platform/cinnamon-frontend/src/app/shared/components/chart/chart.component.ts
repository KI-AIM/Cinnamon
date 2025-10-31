import { Component, OnChanges, OnInit } from '@angular/core';
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

    protected graphOptions(simple: boolean, margins: boolean = true): EChartsCoreOption {
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
        };
    }

    protected formatNumber(value: number | string, dataType: DataType | null): string {
        return this.statisticsService.formatNumber(value, {dataType, max: 3, min: 3});
    }
}

// Stolen from https://stackoverflow.com/questions/60141960/typescript-key-value-relation-preserving-object-entries-type
export type Entries<T> = {
    [K in keyof T]: [K, T[K]];
}[keyof T][];
