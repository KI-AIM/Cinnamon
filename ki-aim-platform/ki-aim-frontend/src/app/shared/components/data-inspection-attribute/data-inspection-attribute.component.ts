import {Component, Input, OnInit, TemplateRef} from '@angular/core';
import {ColumnConfiguration} from "../../model/column-configuration";
import {MatDialog} from "@angular/material/dialog";
import { ECharts, EChartsOption } from "echarts";

@Component({
    selector: 'app-data-inspection-attribute',
    templateUrl: './data-inspection-attribute.component.html',
    styleUrls: ['./data-inspection-attribute.component.less']
})
export class DataInspectionAttributeComponent implements OnInit {
    @Input() public configuration!: ColumnConfiguration;

    protected options: EChartsOption;
    private chartInstances: ECharts;

    constructor(
        private matDialog: MatDialog,
    ) {
    }

    ngOnInit(): void {
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

    protected openDetailsDialog(templateRef: TemplateRef<any>) {
        this.matDialog.open(templateRef, {
            width: '60%'
        });
        this.downloadGraph();
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
        a.download = this.configuration.name +  '-mean.jpeg';
        a.click();
    }
}
