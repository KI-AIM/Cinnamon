import {Component, Input} from '@angular/core';
import {ECharts, EChartsOption} from "echarts";
import {FrequencyPlotData} from "../../model/statistics";

@Component({
    selector: 'app-chart-frequency',
    templateUrl: './chart-frequency.component.html',
    styleUrls: ['./chart-frequency.component.less']
})
export class ChartFrequencyComponent {
    protected options: EChartsOption;
    private chartInstances: ECharts;

    @Input() data!: FrequencyPlotData;

    ngOnInit() {
        this.graphOptions();
    }

    protected onChartInit(event: ECharts) {
        this.chartInstances = event;
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

    private graphOptions(): void {
        const keys = Object.keys(this.data.frequencies);

        this.options = {
            grid: {
                left: 100,
                top: 50,
                right: 100,
                bottom: 50,
                borderWidth: 1,
                borderColor: '#ccc',
                show: true
            },
            // @ts-ignore
            tooltip: {
            },
            xAxis: {
                type: 'category',
                data: keys,
                name: this.data["x-axis"],
                nameLocation: 'middle',
            },
            yAxis: {
                name: this.data["y-axis"],
                minInterval: 1,
            },
            series: [
                {
                    name: this.data["y-axis"],
                    type: 'bar',
                    data: Object.values(this.data.frequencies),
                },
            ],
        };

        if (keys.length > 10) {
            this.options.dataZoom = [
                {
                    type: 'slider',
                    show: true,
                    xAxisIndex: [0],
                    start: 0,
                    end: (10 / keys.length) * 100,
                },
            ];
        }
    }
}
