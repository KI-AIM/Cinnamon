import { Component, Input, OnInit } from '@angular/core';
import { ECharts, EChartsOption } from "echarts";
import { DensityPlotData } from "../../model/statistics";
import { number } from "echarts/types/dist/echarts";

@Component({
  selector: 'app-chart-density',
  templateUrl: './chart-density.component.html',
  styleUrls: ['./chart-density.component.less']
})
export class ChartDensityComponent implements OnInit {
    protected options: EChartsOption;
    private chartInstances: ECharts;

    @Input() data!: DensityPlotData;

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

    private graphOptions() : void {
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
                valueFormatter: (value: number, dataIndex: number) => value.toFixed(3),
            },
            xAxis: {
                data: this.data.x_values,
                name: this.data["x-axis"],
                axisLabel: {
                    formatter: (value: string, index: number) => parseFloat(value).toFixed(3),
                },
            },
            yAxis: {
                name: this.data["y-axis"],
            },
            series: [
                {
                    name: this.data["y-axis"],
                    type: 'line',
                    data: this.data.density,
                },
            ],
        };

    }
}
