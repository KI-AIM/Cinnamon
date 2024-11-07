import {Component, OnInit} from '@angular/core';
import {ECharts, EChartsOption} from "echarts";
import {DataType} from "../../model/data-type";

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

    protected formatNumber(value: number | string, dataType: DataType | null): string {
        if (typeof value === "string") {
            value = parseFloat(value);
        }

        if (dataType) {
            if (dataType === 'DATE') {
                return new Date(value).toLocaleDateString();
            } else if (dataType === 'DATE_TIME') {
                return new Date(value).toLocaleString();
            }
        }

        if (value === 0) {
            return '0';
        }

        const abs = Math.abs(value);
        if (abs > 1000 || abs < 0.001) {
            return value.toExponential(3)
        }
        return value.toFixed(3);
    }
}
