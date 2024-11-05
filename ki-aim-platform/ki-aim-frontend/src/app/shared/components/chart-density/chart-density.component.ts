import { Component, Input, OnInit } from '@angular/core';
import { ECharts, EChartsOption } from "echarts";
import { DensityPlotData } from "../../model/statistics";

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
            // legend: {
            //     data: ['bar', 'bar2'],
            //     align: 'left',
            //     top: 20,
            // },
            grid: {
                left: 50,
                top: 50,
                right: 30,
                bottom: 50,
                borderWidth: 1,
                borderColor: '#ccc',
                show: true
            },
            tooltip: {},
            xAxis: {
                data: this.data.x_values,
                name: this.data["x-axis"],
                silent: false,
                splitLine: {
                    show: false,
                },
            },
            yAxis: {},
            series: [
                {
                    name: this.data["x-axis"],
                    type: 'line',
                    data: this.data.density,
                },
            ],
            animationEasing: 'elasticOut',
        };

    }



}
