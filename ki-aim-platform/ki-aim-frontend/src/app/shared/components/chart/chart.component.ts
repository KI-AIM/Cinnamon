import {Component, OnInit} from '@angular/core';
import {ECharts, EChartsOption} from "echarts";

@Component({
    selector: 'app-chart',
    templateUrl: './chart.component.html',
    styleUrls: ['./chart.component.less']
})
export class ChartComponent implements OnInit {
    protected options: EChartsOption;
    private chartInstances: ECharts;

    ngOnInit() {
        this.options = this.createChartOptions();
    }

    protected onChartInit(event: ECharts) {
        this.chartInstances = event;
    }

    protected createChartOptions(): EChartsOption {
        return this.graphOptions();
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

    protected graphOptions(): EChartsOption {
        return {
            grid: {
                left: 50,
                top: 50,
                right: 20,
                bottom: 50,
                borderWidth: 1,
                borderColor: '#ccc',
                show: true
            },
        };

    }
}
