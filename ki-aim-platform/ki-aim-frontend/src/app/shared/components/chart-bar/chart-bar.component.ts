import { Component, OnInit } from '@angular/core';
import { ECharts, EChartsOption } from "echarts";

@Component({
  selector: 'app-chart-bar',
  templateUrl: './chart-bar.component.html',
  styleUrls: ['./chart-bar.component.less']
})
export class ChartBarComponent implements OnInit {
    protected options: EChartsOption;
    private chartInstances: ECharts;

    ngOnInit() {
        this.demoGraphOptions();
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

    private demoGraphOptions() : void {
        const xAxisData = [];
        const data1 = [];
        const data2 = [];

        for (let i = 0; i < 100; i++) {
            xAxisData.push('category' + i);
            data1.push((Math.sin(i / 5) * (i / 5 - 10) + i / 6) * 5);
            data2.push((Math.cos(i / 5) * (i / 5 - 10) + i / 6) * 5);
        }

        this.options = {
            legend: {
                data: ['bar', 'bar2'],
                align: 'left',
                top: 20,
            },
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
                data: xAxisData,
                silent: false,
                splitLine: {
                    show: false,
                },
            },
            yAxis: {},
            series: [
                {
                    name: 'bar',
                    type: 'bar',
                    data: data1,
                    animationDelay: idx => idx * 10,
                    animationDelayUpdate: idx => idx * 5,
                },
                {
                    name: 'bar2',
                    type: 'bar',
                    data: data2,
                    animationDelay: idx => idx * 10 + 100,
                    animationDelayUpdate: idx => idx * 5,
                },
            ],
            animationEasing: 'elasticOut',
        };

    }


}
